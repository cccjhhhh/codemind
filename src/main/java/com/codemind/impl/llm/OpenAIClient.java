package com.codemind.impl.llm;

import com.codemind.api.llm.*;
import okhttp3.*;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;

import java.io.IOException;
import java.util.List;
import java.util.Map;

/**
 * OpenAI LLM 客户端实现
 * 
 * 学习要点：
 * - HTTP API 调用封装
 * - JSON 请求/响应处理
 * - 流式响应处理（Server-Sent Events）
 */
public class OpenAIClient implements LLMClient {
    
    private static final MediaType JSON = MediaType.get("application/json; charset=utf-8");
    private static final ObjectMapper MAPPER = new ObjectMapper();
    
    private final OkHttpClient httpClient;
    private final String apiKey;
    private final String baseUrl;
    private final String model;
    private final int maxTokens;
    private final double temperature;
    
    public OpenAIClient(String apiKey) {
        this(apiKey, "https://api.openai.com/v1", "gpt-4", 4096, 0.7);
    }
    
    public OpenAIClient(String apiKey, String baseUrl, String model, int maxTokens, double temperature) {
        this.apiKey = apiKey;
        this.baseUrl = baseUrl;
        this.model = model;
        this.maxTokens = maxTokens;
        this.temperature = temperature;
        this.httpClient = new OkHttpClient.Builder().build();
    }
    
    @Override
    public LLMResponse chat(List<Message> messages) {
        try {
            // 1. 构建请求体
            String messagesJson = buildMessagesJson(messages);
            RequestBody body = buildRequestBody(messagesJson, null);
            
            // 2. 创建 HTTP 请求
            Request request = new Request.Builder()
                .url(baseUrl + "/chat/completions")
                .addHeader("Authorization", "Bearer " + apiKey)
                .post(body)
                .build();
            
            // 3. 发送请求并获取响应
            try (Response response = httpClient.newCall(request).execute()) {
                if (!response.isSuccessful()) {
                    String errorBody = response.body() != null ? response.body().string() : "Unknown error";
                    throw new IOException("API 请求失败: " + response.code() + " - " + errorBody);
                }
                
                // 4. 解析响应
                String responseBody = response.body().string();
                return parseResponse(responseBody);
            }
            
        } catch (Exception e) {
            throw new RuntimeException("调用 LLM API 失败", e);
        }
    }
    
    /**
     * 解析 OpenAI API 响应
     */
    private LLMResponse parseResponse(String responseBody) throws Exception {
        JsonNode root = MAPPER.readTree(responseBody);
        JsonNode choices = root.path("choices");
        
        if (choices.isEmpty()) {
            return new LLMResponse("", null, true, 0);
        }
        
        JsonNode firstChoice = choices.get(0);
        JsonNode message = firstChoice.path("message");
        
        String content = message.path("content").asText("");
        boolean finished = "stop".equals(firstChoice.path("finish_reason").asText());
        
        // 解析 token 使用量
        int tokensUsed = root.path("usage").path("total_tokens").asInt(0);
        
        // 解析工具调用（如果有）
        List<ToolCall> toolCalls = null;
        JsonNode toolCallsNode = message.path("tool_calls");
        if (toolCallsNode.isArray() && !toolCallsNode.isEmpty()) {
            toolCalls = new java.util.ArrayList<>();
            for (JsonNode tc : toolCallsNode) {
                String id = tc.path("id").asText();
                String name = tc.path("function").path("name").asText();
                String argsJson = tc.path("function").path("arguments").asText();
                
                // 解析参数 JSON
                Map<String, Object> args = MAPPER.readValue(argsJson, 
                    new com.fasterxml.jackson.core.type.TypeReference<Map<String, Object>>() {});
                
                toolCalls.add(new ToolCall(id, name, args));
            }
        }
        
        return new LLMResponse(content, toolCalls, finished, tokensUsed);
    }
    
    /**
     * 构建请求体（支持 Function Calling）
     */
    private RequestBody buildRequestBody(String messagesJson, List<ToolDefinition> tools) throws Exception {
        ObjectNode body = MAPPER.createObjectNode();
        body.put("model", model);
        body.put("max_tokens", maxTokens);
        body.put("temperature", temperature);
        body.set("messages", MAPPER.readTree(messagesJson));

        if (tools != null && !tools.isEmpty()) {
            ArrayNode toolsArray = body.putArray("tools");
            for (ToolDefinition tool : tools) {
                toolsArray.add(toolToJson(tool));
            }
        }

        return RequestBody.create(body.toString(), JSON);
    }

    /**
     * 将消息列表转换为 JSON 数组
     */
    private String buildMessagesJson(List<Message> messages) throws Exception {
        ArrayNode array = MAPPER.createArrayNode();
        for (Message msg : messages) {
            ObjectNode node = array.addObject();
            node.put("role", msg.getRole().name().toLowerCase());
            node.put("content", msg.getContent());
        }
        return MAPPER.writeValueAsString(array);
    }
    
    @Override
    public LLMResponse chatWithTools(List<Message> messages, List<ToolDefinition> tools) {
        try {
            // 1. 构建请求体（带工具定义）
            String messagesJson = buildMessagesJson(messages);
            RequestBody body = buildRequestBody(messagesJson, tools);
            
            // 2. 创建 HTTP 请求
            Request request = new Request.Builder()
                .url(baseUrl + "/chat/completions")
                .addHeader("Authorization", "Bearer " + apiKey)
                .post(body)
                .build();
            
            // 3. 发送请求并获取响应
            try (Response response = httpClient.newCall(request).execute()) {
                if (!response.isSuccessful()) {
                    String errorBody = response.body() != null ? response.body().string() : "Unknown error";
                    throw new IOException("API 请求失败: " + response.code() + " - " + errorBody);
                }
                
                // 4. 解析响应（与 chat() 相同）
                String responseBody = response.body().string();
                return parseResponse(responseBody);
            }
            
        } catch (Exception e) {
            throw new RuntimeException("调用 LLM API (with tools) 失败", e);
        }
    }
    
    /**
     * 流式调用 LLM（SSE - Server-Sent Events）
     * 
     * 学习要点：
     * - Server-Sent Events (SSE) 协议
     * - 流式数据处理
     * - 增量响应渲染
     */
    @Override
    public void chatStream(List<Message> messages, StreamHandler handler) {
        try {
            // 1. 构建请求体
            String messagesJson = buildMessagesJson(messages);
            RequestBody body = buildRequestBody(messagesJson, null);
            
            // 2. 创建 HTTP 请求（GET 改为流式 POST）
            Request request = new Request.Builder()
                .url(baseUrl + "/chat/completions")
                .addHeader("Authorization", "Bearer " + apiKey)
                .addHeader("Accept", "text/event-stream")
                .post(body)
                .build();
            
            // 3. 发送请求
            httpClient.newCall(request).enqueue(new Callback() {
                private final StringBuilder fullResponse = new StringBuilder();
                
                @Override
                public void onFailure(Call call, IOException e) {
                    handler.onError(e);
                }
                
                @Override
                public void onResponse(Call call, Response response) throws IOException {
                    if (!response.isSuccessful()) {
                        handler.onError(new IOException("API 请求失败: " + response.code()));
                        return;
                    }
                    
                    try (var bodyStream = response.body()) {
                        if (bodyStream == null) {
                            handler.onError(new IOException("空响应体"));
                            return;
                        }
                        
                        // 使用 BufferedSource 读取流
                        var source = bodyStream.source();
                        
                        // 读取所有数据
                        while (true) {
                            // 尝试读取一行（SSE 格式）
                            String line = source.readUtf8Line();
                            if (line == null) {
                                // 流结束
                                break;
                            }
                            
                            // 解析 SSE 格式: data: {...}
                            if (line.startsWith("data: ")) {
                                String json = line.substring(6);
                                if ("[DONE]".equals(json)) {
                                    handler.onComplete(fullResponse.toString());
                                    return;
                                }
                                
                                // 解析并提取增量内容
                                String token = extractDeltaToken(json);
                                if (token != null) {
                                    fullResponse.append(token);
                                    handler.onToken(token);
                                }
                            }
                        }
                        
                        handler.onComplete(fullResponse.toString());
                    }
                }
                
                /**
                 * 从 SSE data 中提取增量 token
                 */
                private String extractDeltaToken(String json) {
                    try {
                        JsonNode node = MAPPER.readTree(json);
                        JsonNode choices = node.path("choices");
                        if (choices.isArray() && !choices.isEmpty()) {
                            return choices.get(0).path("delta").path("content").asText(null);
                        }
                    } catch (Exception e) {
                        // 忽略解析错误
                    }
                    return null;
                }
            });
            
        } catch (Exception e) {
            handler.onError(e);
        }
    }
    
    /**
     * 将工具定义转换为 OpenAI 格式
     */
    private JsonNode toolToJson(ToolDefinition tool) {
        ObjectNode node = MAPPER.createObjectNode();
        node.put("type", "function");
        
        ObjectNode function = node.putObject("function");
        function.put("name", tool.getFunction().getName());
        function.put("description", tool.getFunction().getDescription());
        function.set("parameters", tool.getFunction().getParameters());
        
        return node;
    }
}