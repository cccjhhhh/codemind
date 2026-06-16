package com.codemind.mcp;

import com.fasterxml.jackson.databind.JsonNode;

/**
 * MCP 工具定义
 *
 * 表示 MCP 服务器提供的工具。
 */
public class McpToolDefinition {

    private final String name;
    private final String description;
    private final JsonNode inputSchema;

    public McpToolDefinition(String name, String description, JsonNode inputSchema) {
        this.name = name;
        this.description = description;
        this.inputSchema = inputSchema;
    }

    public String getName() {
        return name;
    }

    public String getDescription() {
        return description;
    }

    public JsonNode getInputSchema() {
        return inputSchema;
    }
}
