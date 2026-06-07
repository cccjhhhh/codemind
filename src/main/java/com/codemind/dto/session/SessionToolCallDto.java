package com.codemind.dto.session;

import com.codemind.api.llm.ToolCall;

import java.util.Map;

public class SessionToolCallDto {

    private String id;
    private String name;
    private Map<String, Object> arguments;

    public SessionToolCallDto() {}

    public SessionToolCallDto(String id, String name, Map<String, Object> arguments) {
        this.id = id;
        this.name = name;
        this.arguments = arguments;
    }

    public static SessionToolCallDto fromToolCall(ToolCall tc) {
        return new SessionToolCallDto(tc.getId(), tc.getName(), tc.getArguments());
    }

    public ToolCall toToolCall() {
        return new ToolCall(id, name, arguments);
    }

    // Getters and setters for Jackson

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }

    public Map<String, Object> getArguments() { return arguments; }
    public void setArguments(Map<String, Object> arguments) { this.arguments = arguments; }
}
