package com.codemind.dto.session;

import com.codemind.api.llm.Message;
import com.codemind.api.llm.ToolCall;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.ArrayList;
import java.util.List;

/**
 * 消息数据传输对象
 * 
 * 用于 JSON 序列化/反序列化消息。
 */
public class MessageDto {
    
    private String role;
    private String content;
    
    @JsonProperty("tool_call_id")
    private String toolCallId;
    
    @JsonProperty("tool_calls")
    private List<ToolCallDto> toolCalls;
    
    // 空的构造函数用于反序列化
    public MessageDto() {}
    
    public MessageDto(String role, String content, String toolCallId, List<ToolCallDto> toolCalls) {
        this.role = role;
        this.content = content;
        this.toolCallId = toolCallId;
        this.toolCalls = toolCalls;
    }
    
    public String getRole() {
        return role;
    }
    
    public String getContent() {
        return content;
    }
    
    public String getToolCallId() {
        return toolCallId;
    }
    
    public List<ToolCallDto> getToolCalls() {
        return toolCalls;
    }
    
    public void setRole(String role) {
        this.role = role;
    }
    
    public void setContent(String content) {
        this.content = content;
    }
    
    public void setToolCallId(String toolCallId) {
        this.toolCallId = toolCallId;
    }
    
    public void setToolCalls(List<ToolCallDto> toolCalls) {
        this.toolCalls = toolCalls;
    }
    
    /**
     * 从 API Message 转换为此 DTO
     */
    public static MessageDto fromMessage(Message message) {
        String role = message.getRole().name().toLowerCase();
        String content = message.getContent();
        String toolCallId = message.getToolCallId();
        
        List<ToolCallDto> toolCalls = null;
        if (message.hasToolCalls()) {
            toolCalls = new ArrayList<>();
            for (ToolCall tc : message.getToolCalls()) {
                toolCalls.add(ToolCallDto.fromToolCall(tc));
            }
        }
        
        return new MessageDto(role, content, toolCallId, toolCalls);
    }
    
    /**
     * 转换为 API Message
     */
    public Message toMessage() {
        Message.Role msgRole = Message.Role.valueOf(role.toUpperCase());
        
        if (toolCallId != null) {
            return new Message(msgRole, content, toolCallId);
        }
        
        if (toolCalls != null && !toolCalls.isEmpty()) {
            List<ToolCall> calls = new ArrayList<>();
            for (ToolCallDto dto : toolCalls) {
                calls.add(dto.toToolCall());
            }
            return new Message(msgRole, content, calls);
        }
        
        return new Message(msgRole, content);
    }
}