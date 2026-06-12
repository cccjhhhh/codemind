package com.codemind.api.llm;

import java.util.List;

/**
 * 消息
 */
public class Message {
    
    public enum Role {
        SYSTEM, USER, ASSISTANT, TOOL
    }
    
    private final Role role;
    private final String content;
    private final String toolCallId;
    private final List<ToolCall> toolCalls;
    
    // 基础构造器
    public Message(Role role, String content) {
        this.role = role;
        this.content = content;
        this.toolCallId = null;
        this.toolCalls = null;
    }
    
    // TOOL 角色构造器
    public Message(Role role, String content, String toolCallId) {
        this.role = role;
        this.content = content;
        this.toolCallId = toolCallId;
        this.toolCalls = null;
    }
    
    // ASSISTANT 角色带 tool_calls 构造器
    public Message(Role role, String content, List<ToolCall> toolCalls) {
        this.role = role;
        this.content = content;
        this.toolCallId = null;
        this.toolCalls = toolCalls;
    }
    
    // ========== 静态工厂方法 ==========
    
    public static Message system(String content) {
        return new Message(Role.SYSTEM, content);
    }
    
    public static Message user(String content) {
        return new Message(Role.USER, content);
    }
    
    public static Message assistant(String content) {
        return new Message(Role.ASSISTANT, content);
    }
    
    public static Message assistantWithTools(String content, List<ToolCall> toolCalls) {
        return new Message(Role.ASSISTANT, content, toolCalls);
    }
    
    public static Message tool(String content, String toolCallId) {
        return new Message(Role.TOOL, content, toolCallId);
    }
    
    // ========== Getter 方法 ==========
    
    public Role getRole() { return role; }
    public String getContent() { return content; }
    public String getToolCallId() { return toolCallId; }
    public List<ToolCall> getToolCalls() { return toolCalls; }
    public boolean hasToolCalls() { return toolCalls != null && !toolCalls.isEmpty(); }

    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder();
        sb.append("[").append(role).append("] ");
        if (content != null) {
            sb.append(content);
        }
        if (toolCallId != null) {
            sb.append(" (toolCallId: ").append(toolCallId).append(")");
        }
        if (toolCalls != null && !toolCalls.isEmpty()) {
            sb.append(" [toolCalls: ").append(toolCalls.size()).append("]");
        }
        return sb.toString();
    }
}