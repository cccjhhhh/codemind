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
import java.util.stream.Collectors;

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
        // TODO: 实现同步调用
        throw new UnsupportedOperationException("Not implemented yet");
    }
    
    @Override
    public void chatStream(List<Message> messages, StreamHandler handler) {
        // TODO: 实现流式调用
        throw new UnsupportedOperationException("Not implemented yet");
    }
    
    @Override
    public LLMResponse chatWithTools(List<Message> messages, List<ToolDefinition> tools) {
        // TODO: 实现 Function Calling
        throw new UnsupportedOperationException("Not implemented yet");
    }
    
    // ============ 内部辅助方法 ============
    
    private String buildMessagesJson(List<Message> messages) throws Exception {
        ArrayNode array = MAPPER.createArrayNode();
        for (Message msg : messages) {
            ObjectNode node = array.addObject();
            node.put("role", msg.getRole().name().toLowerCase());
            node.put("content", msg.getContent());
        }
        return MAPPER.writeValueAsString(array);
    }
    
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