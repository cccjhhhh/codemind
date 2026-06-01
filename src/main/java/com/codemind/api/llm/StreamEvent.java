package com.codemind.api.llm;

import java.util.List;
import java.util.Map;

/**
 * 流式事件
 * 
 * 参考 Claude Code / LangChain 的事件驱动架构。
 * 每个 SSE 消息都转换为一个事件，消费者根据事件类型处理。
 * 
 * 学习要点：
 * - 事件驱动设计
 * - 流式数据的状态管理
 * - 工具调用的增量收集
 */
public class StreamEvent {
    
    /**
     * 事件类型
     */
    public enum Type {
        /** 文本增量 */
        TEXT_DELTA,
        
        /** 工具调用开始 */
        TOOL_CALL_START,
        
        /** 工具调用参数增量 */
        TOOL_CALL_DELTA,
        
        /** 工具调用完成（参数完整，可执行） */
        TOOL_CALL_COMPLETE,
        
        /** 整个消息完成 */
        MESSAGE_COMPLETE,
        
        /** 错误 */
        ERROR
    }
    
    private final Type type;
    
    // TEXT_DELTA 相关
    private final String textDelta;
    
    // TOOL_CALL 相关
    private final int toolCallIndex;
    private final String toolCallId;
    private final String toolCallName;
    private final String toolCallArgsDelta;  // 增量 JSON
    private final Map<String, Object> toolCallArgs;  // 完整参数
    
    // MESSAGE_COMPLETE 相关
    private final String fullText;
    private final List<ToolCall> toolCalls;
    private final int tokensUsed;
    
    // ERROR 相关
    private final Exception error;
    
    // 私有构造器，使用静态工厂方法创建
    private StreamEvent(Type type, String textDelta, int toolCallIndex, 
                        String toolCallId, String toolCallName, 
                        String toolCallArgsDelta, Map<String, Object> toolCallArgs,
                        String fullText, List<ToolCall> toolCalls, int tokensUsed,
                        Exception error) {
        this.type = type;
        this.textDelta = textDelta;
        this.toolCallIndex = toolCallIndex;
        this.toolCallId = toolCallId;
        this.toolCallName = toolCallName;
        this.toolCallArgsDelta = toolCallArgsDelta;
        this.toolCallArgs = toolCallArgs;
        this.fullText = fullText;
        this.toolCalls = toolCalls;
        this.tokensUsed = tokensUsed;
        this.error = error;
    }
    
    // ========== 静态工厂方法 ==========
    
    /**
     * 创建文本增量事件
     */
    public static StreamEvent textDelta(String text) {
        return new StreamEvent(Type.TEXT_DELTA, text, -1, null, null, 
                               null, null, null, null, 0, null);
    }
    
    /**
     * 创建工具调用开始事件
     */
    public static StreamEvent toolCallStart(int index, String id, String name) {
        return new StreamEvent(Type.TOOL_CALL_START, null, index, id, name,
                               null, null, null, null, 0, null);
    }
    
    /**
     * 创建工具调用参数增量事件
     */
    public static StreamEvent toolCallDelta(int index, String argsDelta) {
        return new StreamEvent(Type.TOOL_CALL_DELTA, null, index, null, null,
                               argsDelta, null, null, null, 0, null);
    }
    
    /**
     * 创建工具调用完成事件
     */
    public static StreamEvent toolCallComplete(int index, String id, String name, 
                                                Map<String, Object> args) {
        return new StreamEvent(Type.TOOL_CALL_COMPLETE, null, index, id, name,
                               null, args, null, null, 0, null);
    }
    
    /**
     * 创建消息完成事件
     */
    public static StreamEvent messageComplete(String fullText, List<ToolCall> toolCalls, 
                                               int tokensUsed) {
        return new StreamEvent(Type.MESSAGE_COMPLETE, fullText, -1, null, null,
                               null, null, fullText, toolCalls, tokensUsed, null);
    }
    
    /**
     * 创建错误事件
     */
    public static StreamEvent error(Exception error) {
        return new StreamEvent(Type.ERROR, null, -1, null, null,
                               null, null, null, null, 0, error);
    }
    
    // ========== Getter 方法 ==========
    
    public Type getType() { return type; }
    public String getTextDelta() { return textDelta; }
    public int getToolCallIndex() { return toolCallIndex; }
    public String getToolCallId() { return toolCallId; }
    public String getToolCallName() { return toolCallName; }
    public String getToolCallArgsDelta() { return toolCallArgsDelta; }
    public Map<String, Object> getToolCallArgs() { return toolCallArgs; }
    public String getFullText() { return fullText; }
    public List<ToolCall> getToolCalls() { return toolCalls; }
    public int getTokensUsed() { return tokensUsed; }
    public Exception getError() { return error; }
    
    // ========== 类型判断方法 ==========
    
    public boolean isTextDelta() { return type == Type.TEXT_DELTA; }
    public boolean isToolCallStart() { return type == Type.TOOL_CALL_START; }
    public boolean isToolCallDelta() { return type == Type.TOOL_CALL_DELTA; }
    public boolean isToolCallComplete() { return type == Type.TOOL_CALL_COMPLETE; }
    public boolean isMessageComplete() { return type == Type.MESSAGE_COMPLETE; }
    public boolean isError() { return type == Type.ERROR; }
    public boolean hasToolCalls() { return toolCalls != null && !toolCalls.isEmpty(); }
    
    @Override
    public String toString() {
        switch (type) {
            case TEXT_DELTA:
                return "TEXT_DELTA: \"" + textDelta + "\"";
            case TOOL_CALL_START:
                return "TOOL_CALL_START: " + toolCallName + " (index=" + toolCallIndex + ")";
            case TOOL_CALL_DELTA:
                return "TOOL_CALL_DELTA: \"" + toolCallArgsDelta + "\"";
            case TOOL_CALL_COMPLETE:
                return "TOOL_CALL_COMPLETE: " + toolCallName + "(" + toolCallArgs + ")";
            case MESSAGE_COMPLETE:
                return "MESSAGE_COMPLETE: " + (hasToolCalls() ? toolCalls.size() + " tools" : "text only");
            case ERROR:
                return "ERROR: " + error.getMessage();
            default:
                return type.name();
        }
    }
}