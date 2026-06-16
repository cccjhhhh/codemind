package com.codemind.llm;

import java.util.Map;

/**
 * 工具调用请求
 */
public class ToolCall {
    
    private final String id;
    private final String name;
    private final Map<String, Object> arguments;
    
    public ToolCall(String id, String name, Map<String, Object> arguments) {
        this.id = id;
        this.name = name;
        this.arguments = arguments;
    }
    
    // Getters
    public String getId() { return id; }
    public String getName() { return name; }
    public Map<String, Object> getArguments() { return arguments; }
}
