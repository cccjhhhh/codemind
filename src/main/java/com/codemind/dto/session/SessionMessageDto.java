package com.codemind.dto.session;

import com.codemind.api.llm.Message;
import com.codemind.api.llm.ToolCall;

import java.util.ArrayList;
import java.util.List;

public class SessionMessageDto {

    private String role;
    private String content;
    private String toolCallId;
    private List<SessionToolCallDto> toolCalls;

    public SessionMessageDto() {}

    public SessionMessageDto(String role, String content, String toolCallId, List<SessionToolCallDto> toolCalls) {
        this.role = role;
        this.content = content;
        this.toolCallId = toolCallId;
        this.toolCalls = toolCalls;
    }

    public static SessionMessageDto fromMessage(Message msg) {
        String role = msg.getRole().name().toLowerCase();
        String content = msg.getContent();
        String toolCallId = msg.getToolCallId();
        List<SessionToolCallDto> toolCalls = null;
        if (msg.hasToolCalls()) {
            toolCalls = new ArrayList<>();
            for (ToolCall tc : msg.getToolCalls()) {
                toolCalls.add(SessionToolCallDto.fromToolCall(tc));
            }
        }
        return new SessionMessageDto(role, content, toolCallId, toolCalls);
    }

    public Message toMessage() {
        Message.Role msgRole = Message.Role.valueOf(role.toUpperCase());
        if (toolCallId != null) {
            return new Message(msgRole, content, toolCallId);
        }
        if (toolCalls != null && !toolCalls.isEmpty()) {
            List<ToolCall> calls = new ArrayList<>();
            for (SessionToolCallDto dto : toolCalls) {
                calls.add(dto.toToolCall());
            }
            return new Message(msgRole, content, calls);
        }
        return new Message(msgRole, content);
    }

    // Getters and setters for Jackson

    public String getRole() { return role; }
    public void setRole(String role) { this.role = role; }

    public String getContent() { return content; }
    public void setContent(String content) { this.content = content; }

    public String getToolCallId() { return toolCallId; }
    public void setToolCallId(String toolCallId) { this.toolCallId = toolCallId; }

    public List<SessionToolCallDto> getToolCalls() { return toolCalls; }
    public void setToolCalls(List<SessionToolCallDto> toolCalls) { this.toolCalls = toolCalls; }
}
