package com.codemind.mcp;

import com.codemind.mcp.McpClient;
import com.codemind.mcp.McpToolAdapter;
import com.codemind.mcp.McpToolDefinition;
import com.codemind.tool.spi.Tool;
import com.codemind.tool.ToolResult;
import com.fasterxml.jackson.databind.JsonNode;
import java.util.Map;
import java.util.Optional;

/**
 * MCP 工具适配器实现
 * 
 * 将 MCP 工具定义转换为 CodeMind Tool 接口。
 */
public class McpToolAdapterImpl implements McpToolAdapter {
    
    private static final String PREFIX = "mcp_";
    
    @Override
    public Tool adapt(McpToolDefinition mcpTool, McpClient client) {
        String prefixedName = generatePrefixedName(client.getServerName(), mcpTool.getName());
        
        return new Tool() {
            @Override
            public String getName() {
                return prefixedName;
            }
            
            @Override
            public String getDescription() {
                return mcpTool.getDescription();
            }
            
            @Override
            public JsonNode getInputSchema() {
                return mcpTool.getInputSchema();
            }
            
            @Override
            public ToolResult execute(Map<String, Object> params) {
                try {
                    return client.executeTool(mcpTool.getName(), params);
                } catch (Exception e) {
                    return ToolResult.failure("MCP tool execution failed: " + e.getMessage());
                }
            }
            
            @Override
            public Optional<String> getDeprecatedName() {
                return Optional.empty();
            }
        };
    }
    
    @Override
    public String generatePrefixedName(String serverName, String toolName) {
        return PREFIX + serverName + "_" + toolName;
    }
}