package com.codemind.api.llm;

/**
 * LLM 消息
 */
public class Message {
    
    public enum Role {
        SYSTEM, USER, ASSISTANT, TOOL
    }
    
    private final Role role;
    private final String content;
    private final String toolCallId;
    
    public Message(Role role, String content) {
        this.role = role;
        this.content = content;
        this.toolCallId = null;
    }
    
    public Message(Role role, String content, String toolCallId) {
        this.role = role;
        this.content = content;
        this.toolCallId = toolCallId;
    }
    
    public static Message system(String content) {
        return new Message(Role.SYSTEM, content);
    }
    
    public static Message user(String content) {
        return new Message(Role.USER, content);
    }
    
    public static Message assistant(String content) {
        return new Message(Role.ASSISTANT, content);
    }
    
    public static Message tool(String content, String toolCallId) {
        return new Message(Role.TOOL, content, toolCallId);
    }
    
    // Getters
    public Role getRole() { return role; }
    public String getContent() { return content; }
    public String getToolCallId() { return toolCallId; }
}
