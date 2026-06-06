package com.codemind.dto.llm;

import java.util.List;

/**
 * 消息
 */
public class MessageDto {
    
    public enum Role {
        SYSTEM, USER, ASSISTANT, TOOL
    }
    
    private final Role role;
    private final String content;
    private final String toolCallId;
    private final List<ToolCallDto> toolCalls;
    
    // 基础构造器
    public MessageDto(Role role, String content) {
        this.role = role;
        this.content = content;
        this.toolCallId = null;
        this.toolCalls = null;
    }
    
    // TOOL 角色构造器
    public MessageDto(Role role, String content, String toolCallId) {
        this.role = role;
        this.content = content;
        this.toolCallId = toolCallId;
        this.toolCalls = null;
    }
    
    // ASSISTANT 角色带 tool_calls 构造器
    public MessageDto(Role role, String content, List<ToolCallDto> toolCalls) {
        this.role = role;
        this.content = content;
        this.toolCallId = null;
        this.toolCalls = toolCalls;
    }
    
    // ========== 静态工厂方法 ==========
    
    public static MessageDto system(String content) {
        return new MessageDto(Role.SYSTEM, content);
    }
    
    public static MessageDto user(String content) {
        return new MessageDto(Role.USER, content);
    }
    
    public static MessageDto assistant(String content) {
        return new MessageDto(Role.ASSISTANT, content);
    }
    
    public static MessageDto assistantWithTools(String content, List<ToolCallDto> toolCalls) {
        return new MessageDto(Role.ASSISTANT, content, toolCalls);
    }
    
    public static MessageDto tool(String content, String toolCallId) {
        return new MessageDto(Role.TOOL, content, toolCallId);
    }
    
    // ========== Getter 方法 ==========
    
    public Role getRole() { return role; }
    public String getContent() { return content; }
    public String getToolCallId() { return toolCallId; }
    public List<ToolCallDto> getToolCalls() { return toolCalls; }
    public boolean hasToolCalls() { return toolCalls != null && !toolCalls.isEmpty(); }
}
