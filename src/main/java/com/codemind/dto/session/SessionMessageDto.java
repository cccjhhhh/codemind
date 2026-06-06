package com.codemind.dto.session;

import com.codemind.api.llm.Message;
import com.codemind.api.llm.ToolCall;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.ArrayList;
import java.util.List;

/**
 * 会话消息数据传输对象
 *
 * 用于会话持久化时的 JSON 序列化/反序列化消息。
 * 与 LLM API 的 MessageDto 不同，此 DTO 用于会话状态保存。
 */
public class SessionMessageDto {

    private String role;
    private String content;

    @JsonProperty("tool_call_id")
    private String toolCallId;

    @JsonProperty("tool_calls")
    private List<SessionToolCallDto> toolCalls;

    // 空的构造函数用于反序列化
    public SessionMessageDto() {}

    public SessionMessageDto(String role, String content, String toolCallId, List<SessionToolCallDto> toolCalls) {
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

    public List<SessionToolCallDto> getToolCalls() {
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

    public void setToolCalls(List<SessionToolCallDto> toolCalls) {
        this.toolCalls = toolCalls;
    }

    /**
     * 从 API Message 转换为此 DTO
     */
    public static SessionMessageDto fromMessage(Message message) {
        String role = message.getRole().name().toLowerCase();
        String content = message.getContent();
        String toolCallId = message.getToolCallId();

        List<SessionToolCallDto> toolCalls = null;
        if (message.hasToolCalls()) {
            toolCalls = new ArrayList<>();
            for (ToolCall tc : message.getToolCalls()) {
                toolCalls.add(SessionToolCallDto.fromToolCall(tc));
            }
        }

        return new SessionMessageDto(role, content, toolCallId, toolCalls);
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
            for (SessionToolCallDto dto : toolCalls) {
                calls.add(dto.toToolCall());
            }
            return new Message(msgRole, content, calls);
        }

        return new Message(msgRole, content);
    }
}
