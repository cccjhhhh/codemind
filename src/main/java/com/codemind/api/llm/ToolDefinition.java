package com.codemind.api.llm;

import com.fasterxml.jackson.databind.JsonNode;

/**
 * 工具定义
 * 
 * 用于 Function Calling 的工具描述
 */
public class ToolDefinition {
    
    private final String type = "function";
    private final FunctionDefinition function;
    
    public ToolDefinition(String name, String description, JsonNode parameters) {
        this.function = new FunctionDefinition(name, description, parameters);
    }
    
    // Getters
    public String getType() { return type; }
    public FunctionDefinition getFunction() { return function; }
    
    public static class FunctionDefinition {
        private final String name;
        private final String description;
        private final JsonNode parameters;
        
        public FunctionDefinition(String name, String description, JsonNode parameters) {
            this.name = name;
            this.description = description;
            this.parameters = parameters;
        }
        
        // Getters
        public String getName() { return name; }
        public String getDescription() { return description; }
        public JsonNode getParameters() { return parameters; }
    }
}
