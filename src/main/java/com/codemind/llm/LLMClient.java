package com.codemind.llm;

import java.util.List;

/**
 * LLM 客户端接口
 * 
 * 封装与 LLM API 的交互，支持同步和流式调用。
 */
public interface LLMClient {
    
    /**
     * 同步调用 LLM
     */
    LLMResponse chat(List<Message> messages);
    
    /**
     * 流式调用 LLM（仅文本，无工具调用）
     */
    void chatStream(List<Message> messages, StreamHandler handler);
    
    /**
     * 流式调用 LLM（支持工具调用）
     * 
     * 通过 handler 回调流式事件，包括：
     * - TEXT_DELTA: 文本增量
     * - TOOL_CALL_START: 工具调用开始
     * - TOOL_CALL_DELTA: 工具参数增量
     * - TOOL_CALL_COMPLETE: 工具调用完成（参数完整）
     * - MESSAGE_COMPLETE: 消息完成
     * - ERROR: 错误
     */
    void chatStreamWithTools(List<Message> messages, List<ToolDefinition> tools, 
                            StreamHandler handler);
    
    /**
     * 支持 Function Calling 的同步调用
     */
    LLMResponse chatWithTools(List<Message> messages, List<ToolDefinition> tools);
    
    /**
     * 流式响应处理器
     * 
     * 注意：此接口已简化，事件类型通过 StreamEvent 传递
     */
    interface StreamHandler {
        
        /**
         * 处理流式事件
         */
        void onEvent(StreamEvent event);
        
        /**
         * 流式结束（保留向后兼容）
         */
        default void onComplete(String fullResponse) {}
        
        /**
         * 错误发生（保留向后兼容）
         */
        default void onError(Exception e) {}
    }
}