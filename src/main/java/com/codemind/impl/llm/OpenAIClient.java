package com.codemind.impl.llm;

import com.codemind.api.llm.*;
import com.codemind.exception.ContextLengthException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import okhttp3.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * OpenAI LLM 客户端实现
 * 
 */
public class OpenAIClient implements LLMClient {
    
    private static final Logger log = LoggerFactory.getLogger(OpenAIClient.class);
    private static final MediaType JSON_TYPE = MediaType.get("application/json; charset=utf-8");
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
        this.httpClient = new OkHttpClient.Builder()
            .connectTimeout(30, java.util.concurrent.TimeUnit.SECONDS)
            .readTimeout(120, java.util.concurrent.TimeUnit.SECONDS)
            .build();
    }
    
    @Override
    public LLMResponse chat(List<Message> messages) {
        try {
            String messagesJson = buildMessagesJson(messages);
            RequestBody body = buildRequestBody(messagesJson, null, false);
            
            Request request = new Request.Builder()
                .url(baseUrl + "/chat/completions")
                .addHeader("Authorization", "Bearer " + apiKey)
                .post(body)
                .build();
            
            try (Response response = httpClient.newCall(request).execute()) {
                if (!response.isSuccessful()) {
                    String errorBody = response.body() != null ? response.body().string() : "Unknown error";
                    throw new IOException("API 请求失败: " + response.code() + " - " + errorBody);
                }
                return parseResponse(response.body().string());
            }
        } catch (Exception e) {
            if (isContextLengthError(e)) {
                throw new ContextLengthException("LLM API 上下文长度超限", e);
            }
            throw new RuntimeException("调用 LLM API 失败", e);
        }
    }

    @Override
    public LLMResponse chatWithTools(List<Message> messages, List<ToolDefinition> tools) {
        try {
            String messagesJson = buildMessagesJson(messages);
            RequestBody body = buildRequestBody(messagesJson, tools, false);
            
            Request request = new Request.Builder()
                .url(baseUrl + "/chat/completions")
                .addHeader("Authorization", "Bearer " + apiKey)
                .post(body)
                .build();
            
            try (Response response = httpClient.newCall(request).execute()) {
                if (!response.isSuccessful()) {
                    String errorBody = response.body() != null ? response.body().string() : "Unknown error";
                    throw new IOException("API 请求失败: " + response.code() + " - " + errorBody);
                }
                return parseResponse(response.body().string());
            }
        } catch (Exception e) {
            if (isContextLengthError(e)) {
                throw new ContextLengthException("LLM API 上下文长度超限", e);
            }
            throw new RuntimeException("调用 LLM API (with tools) 失败", e);
        }
    }

    @Override
    public void chatStream(List<Message> messages, StreamHandler handler) {
        chatStreamWithTools(messages, null, handler);
    }
    
    @Override
    public void chatStreamWithTools(List<Message> messages, List<ToolDefinition> tools, 
                                     StreamHandler handler) {
        try {
            String messagesJson = buildMessagesJson(messages);
            RequestBody body = buildRequestBody(messagesJson, tools, true);
            
            Request request = new Request.Builder()
                .url(baseUrl + "/chat/completions")
                .addHeader("Authorization", "Bearer " + apiKey)
                .addHeader("Accept", "text/event-stream")
                .post(body)
                .build();
            
            httpClient.newCall(request).enqueue(new Callback() {
                
                @Override
                public void onFailure(Call call, IOException e) {
                    handler.onEvent(StreamEvent.error(e));
                    handler.onError(e);
                }
                
                @Override
                public void onResponse(Call call, Response response) throws IOException {
                    if (!response.isSuccessful()) {
                        String errorBody = response.body() != null ? response.body().string() : "Unknown error";
                        IOException e = new IOException("API 请求失败: " + response.code() + " - " + errorBody);
                        handler.onEvent(StreamEvent.error(e));
                        handler.onError(e);
                        return;
                    }
                    
                    try (ResponseBody body = response.body()) {
                        if (body == null) {
                            handler.onEvent(StreamEvent.error(new IOException("空响应体")));
                            return;
                        }
                        
                        // 状态收集器
                        StringBuilder fullText = new StringBuilder();
                        List<ToolCall> toolCalls = new ArrayList<>();
                        Map<Integer, StringBuilder> toolArgsBuffers = new ConcurrentHashMap<>();
                        Map<Integer, String> toolIds = new ConcurrentHashMap<>();
                        Map<Integer, String> toolNames = new ConcurrentHashMap<>();
                        int totalTokens = 0;
                        
                        var source = body.source();
                        
                        while (true) {
                            String line = source.readUtf8Line();
                            if (line == null) break;
                            
                            line = line.trim();
                            if (line.isEmpty()) continue;
                            
                            // SSE 格式: data: {...}
                            if (line.startsWith("data: ")) {
                                String json = line.substring(6);
                                
                                if ("[DONE]".equals(json)) {
                                    // 流结束，发送 MESSAGE_COMPLETE
                                    StreamEvent complete = StreamEvent.messageComplete(
                                        fullText.toString(), 
                                        toolCalls, 
                                        totalTokens
                                    );
                                    handler.onEvent(complete);
                                    handler.onComplete(fullText.toString());
                                    return;
                                }
                                
                                // 解析事件
                                try {
                                    JsonNode root = MAPPER.readTree(json);
                                    JsonNode choices = root.path("choices");
                                    
                                    if (choices.isArray() && !choices.isEmpty()) {
                                        JsonNode choice = choices.get(0);
                                        JsonNode delta = choice.path("delta");
                                        
                                        // 1. 处理文本增量
                                        JsonNode contentNode = delta.get("content");
                                        if (contentNode != null && !contentNode.isNull()) {
                                            String text = contentNode.asText();
                                            if (text != null && !text.isEmpty()) {
                                                fullText.append(text);
                                                handler.onEvent(StreamEvent.textDelta(text));
                                            }
                                        }
                                        
                                        // 2. 处理工具调用增量
                                        JsonNode toolCallsDelta = delta.path("tool_calls");
                                        if (toolCallsDelta.isArray()) {
                                            for (JsonNode tc : toolCallsDelta) {
                                                int index = tc.path("index").asInt();
                                                
                                                // 工具调用开始
                                                if (tc.has("id")) {
                                                    String id = tc.path("id").asText();
                                                    String name = tc.path("function").path("name").asText();
                                                    
                                                    toolIds.put(index, id);
                                                    toolNames.put(index, name);
                                                    toolArgsBuffers.put(index, new StringBuilder());
                                                    
                                                    handler.onEvent(StreamEvent.toolCallStart(index, id, name));
                                                }
                                                
                                                // 工具参数增量
                                                JsonNode funcDelta = tc.path("function");
                                                if (funcDelta.has("arguments")) {
                                                    String argsChunk = funcDelta.path("arguments").asText();
                                                    if (argsChunk != null && !argsChunk.isEmpty()) {
                                                        toolArgsBuffers.get(index).append(argsChunk);
                                                        handler.onEvent(StreamEvent.toolCallDelta(index, argsChunk));
                                                    }
                                                }
                                            }
                                        }
                                        
                                        // 3. 检查 finish_reason
                                        String finishReason = choice.path("finish_reason").asText(null);
                                        if (finishReason != null && !finishReason.isEmpty()) {
                                            // finish_reason 有值，表示这一轮对话结束
                                            
                                            // 如果是工具调用，构建完整的 ToolCall 列表
                                            if ("tool_calls".equals(finishReason)) {
                                                for (Map.Entry<Integer, StringBuilder> entry : toolArgsBuffers.entrySet()) {
                                                    int index = entry.getKey();
                                                    String id = toolIds.get(index);
                                                    String name = toolNames.get(index);
                                                    String argsJson = entry.getValue().toString();
                                                    
                                                    Map<String, Object> args = parseJsonArgs(argsJson);
                                                    
                                                    ToolCall toolCall = new ToolCall(id, name, args);
                                                    toolCalls.add(toolCall);
                                                    
                                                    handler.onEvent(StreamEvent.toolCallComplete(index, id, name, args));
                                                }
                                            }
                                            
                                            // 发送 MESSAGE_COMPLETE 事件
                                            StreamEvent complete = StreamEvent.messageComplete(
                                                fullText.toString(), toolCalls, totalTokens
                                            );
                                            handler.onEvent(complete);
                                            handler.onComplete(fullText.toString());
                                            return;
                                        }
                                        
                                        // 4. 处理 token 使用量
                                        JsonNode usage = root.path("usage");
                                        if (usage.has("total_tokens")) {
                                            totalTokens = usage.path("total_tokens").asInt();
                                        }
                                    }
                                } catch (Exception parseError) {
                                    log.debug("SSE parse error, skipping chunk: {}", parseError.getMessage());
                                }
                            }
                        }
                        
                        // 流正常结束
                        StreamEvent complete = StreamEvent.messageComplete(
                            fullText.toString(), toolCalls, totalTokens
                        );
                        handler.onEvent(complete);
                        handler.onComplete(fullText.toString());
                    }
                }
            });
            
        } catch (Exception e) {
            handler.onEvent(StreamEvent.error(e));
            handler.onError(e);
        }
    }
    
    /**
     * 解析 JSON 参数
     */
    private Map<String, Object> parseJsonArgs(String json) {
        if (json == null || json.isEmpty()) {
            return new HashMap<>();
        }
        try {
            return MAPPER.readValue(json,
                new com.fasterxml.jackson.core.type.TypeReference<Map<String, Object>>() {});
        } catch (Exception e) {
            log.warn("工具参数 JSON 解析失败: {}", e.getMessage());
            return new HashMap<>();
        }
    }
    
    /**
     * 解析同步响应
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
        int tokensUsed = root.path("usage").path("total_tokens").asInt(0);
        
        List<ToolCall> toolCalls = null;
        JsonNode toolCallsNode = message.path("tool_calls");
        if (toolCallsNode.isArray() && !toolCallsNode.isEmpty()) {
            toolCalls = new ArrayList<>();
            for (JsonNode tc : toolCallsNode) {
                String id = tc.path("id").asText();
                String name = tc.path("function").path("name").asText();
                String argsJson = tc.path("function").path("arguments").asText();
                Map<String, Object> args = parseJsonArgs(argsJson);
                toolCalls.add(new ToolCall(id, name, args));
            }
        }
        
        return new LLMResponse(content, toolCalls, finished, tokensUsed);
    }
    
    /**
     * 构建请求体
     */
    private RequestBody buildRequestBody(String messagesJson, List<ToolDefinition> tools, 
                                          boolean stream) throws Exception {
        ObjectNode body = MAPPER.createObjectNode();
        body.put("model", model);
        body.put("max_tokens", maxTokens);
        body.put("temperature", temperature);
        body.put("stream", stream);
        body.set("messages", MAPPER.readTree(messagesJson));
        
        if (tools != null && !tools.isEmpty()) {
            ArrayNode toolsArray = body.putArray("tools");
            for (ToolDefinition tool : tools) {
                toolsArray.add(toolToJson(tool));
            }
        }
        
        return RequestBody.create(body.toString(), JSON_TYPE);
    }
    
    /**
     * 构建消息 JSON
     */
    private String buildMessagesJson(List<Message> messages) throws Exception {
        ArrayNode array = MAPPER.createArrayNode();
        for (Message msg : messages) {
            ObjectNode node = array.addObject();
            String role = msg.getRole().name().toLowerCase();
            node.put("role", role);

            // TOOL 角色需要 tool_call_id 字段
            if (msg.getRole() == Message.Role.TOOL) {
                String toolCallId = msg.getToolCallId();
                if (toolCallId == null) {
                    log.warn("TOOL message with null tool_call_id, content length: {}",
                        msg.getContent() != null ? msg.getContent().length() : 0);
                    toolCallId = "null_tool_call_id";
                }
                node.put("tool_call_id", toolCallId);
            }

            // ASSISTANT 角色可能有 tool_calls
            if (msg.getRole() == Message.Role.ASSISTANT && msg.hasToolCalls()) {
                ArrayNode toolCallsArray = node.putArray("tool_calls");
                for (ToolCall tc : msg.getToolCalls()) {
                    ObjectNode tcNode = toolCallsArray.addObject();
                    tcNode.put("id", tc.getId());
                    tcNode.put("type", "function");
                    ObjectNode funcNode = tcNode.putObject("function");
                    funcNode.put("name", tc.getName());
                    funcNode.put("arguments", MAPPER.writeValueAsString(tc.getArguments()));
                }
            }

            // content 字段：Assistant 带 tool_calls 时设为 null（部分 API 要求），否则可为空字符串
            if (msg.getRole() == Message.Role.ASSISTANT && msg.hasToolCalls()) {
                node.putNull("content");
            } else {
                String content = msg.getContent();
                node.put("content", content != null ? content : "");
            }
        }
        String result = MAPPER.writeValueAsString(array);
        // 记录消息角色序列，用于调试 TOOL/ASSISTANT 配对问题
        StringBuilder roles = new StringBuilder();
        for (Message msg : messages) {
            if (roles.length() > 0) roles.append(" → ");
            roles.append(msg.getRole().name());
            if (msg.getRole() == Message.Role.ASSISTANT && msg.hasToolCalls()) {
                roles.append("(").append(msg.getToolCalls().size()).append(" tools)");
            }
        }
        log.debug("消息序列: {} (共{}条)", roles, messages.size());
        if (log.isDebugEnabled()) {
            log.debug("Messages JSON (first 500 chars): {}", result.substring(0, Math.min(500, result.length())));
        }
        return result;
    }
    
    /**
     * 工具定义转 JSON
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

    /**
     * 判断异常是否由上下文长度超限引起（遍历 cause 链）。
     */
    private static boolean isContextLengthError(Exception e) {
        for (Throwable t = e; t != null; t = t.getCause()) {
            String msg = t.getMessage();
            if (msg != null && (msg.contains("prompt_too_long")
                    || msg.contains("context_length_exceeded")
                    || msg.contains("maximum context length"))) {
                return true;
            }
        }
        return false;
    }
}