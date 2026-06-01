package com.codemind.api.llm;

import java.util.List;

/**
 * LLM 客户端接口
 * 
 * 封装与 LLM API 的交互，支持同步和流式调用。
 * 学习要点：API 调用封装、流式响应处理、Function Calling 协议
 */
public interface LLMClient {
    
    /**
     * 同步调用 LLM
     */
    LLMResponse chat(List<Message> messages);
    
    /**
     * 流式调用 LLM
     */
    void chatStream(List<Message> messages, StreamHandler handler);
    
    /**
     * 支持 Function Calling 的调用
     */
    LLMResponse chatWithTools(List<Message> messages, List<ToolDefinition> tools);
    
    /**
     * 流式响应处理器
     */
    interface StreamHandler {
        void onToken(String token);
        void onComplete(String fullResponse);
        void onError(Exception e);
    }
}
