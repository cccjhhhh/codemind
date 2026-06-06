package com.codemind.dto.session;

import com.codemind.api.llm.ToolCall;

import java.util.Map;

/**
 * 会话工具调用数据传输对象
 *
 * 用于会话持久化时的 JSON 序列化/反序列化工具调用。
 * 与 LLM API 的 ToolCallDto 不同，此 DTO 用于会话状态保存。
 */
public class SessionToolCallDto {

    private String id;
    private String name;
    private Map<String, Object> arguments;

    // 空的构造函数用于反序列化
    public SessionToolCallDto() {}

    public SessionToolCallDto(String id, String name, Map<String, Object> arguments) {
        this.id = id;
        this.name = name;
        this.arguments = arguments;
    }

    public String getId() {
        return id;
    }

    public String getName() {
        return name;
    }

    public Map<String, Object> getArguments() {
        return arguments;
    }

    public void setId(String id) {
        this.id = id;
    }

    public void setName(String name) {
        this.name = name;
    }

    public void setArguments(Map<String, Object> arguments) {
        this.arguments = arguments;
    }

    /**
     * 从 API ToolCall 转换为此 DTO
     */
    public static SessionToolCallDto fromToolCall(ToolCall toolCall) {
        return new SessionToolCallDto(
            toolCall.getId(),
            toolCall.getName(),
            toolCall.getArguments()
        );
    }

    /**
     * 转换为 API ToolCall
     */
    public ToolCall toToolCall() {
        return new ToolCall(id, name, arguments);
    }
}
