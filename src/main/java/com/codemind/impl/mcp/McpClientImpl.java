package com.codemind.impl.mcp;

import com.codemind.api.mcp.McpClient;
import com.codemind.api.mcp.McpServerConfig;
import com.codemind.api.mcp.McpToolDefinition;
import com.codemind.api.tool.ToolResult;
import com.codemind.common.exception.McpConnectionException;
import com.codemind.common.exception.McpOperationException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.modelcontextprotocol.client.McpSyncClient;
import io.modelcontextprotocol.spec.McpClientTransport;
import io.modelcontextprotocol.spec.McpSchema;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * MCP 客户端实现
 * 
 * 基于官方 MCP Java SDK 实现。
 */
public class McpClientImpl implements McpClient {
    
    private static final ObjectMapper MAPPER = new ObjectMapper();
    private final McpTransportFactory transportFactory;
    private McpSyncClient sdkClient;
    private McpServerConfig currentConfig;
    private String serverName;
    
    public McpClientImpl(McpTransportFactory transportFactory) {
        this.transportFactory = transportFactory;
    }
    
    @Override
    public void connect(McpServerConfig config) {
        try {
            this.currentConfig = config;
            this.serverName = config.getCommand() != null ? config.getCommand() : config.getUrl();
            
            McpClientTransport transport = transportFactory.createTransport(config);
            this.sdkClient = io.modelcontextprotocol.client.McpClient.sync(transport)
                .build();
            this.sdkClient.initialize();
        } catch (Exception e) {
            throw new McpConnectionException(serverName, "Failed to connect to MCP server", e);
        }
    }
    
    @Override
    public void disconnect() {
        if (sdkClient != null) {
            try {
                sdkClient.closeGracefully();
            } catch (Exception e) {
                // Ignore close errors
            }
            sdkClient = null;
        }
    }
    
    @Override
    public List<McpToolDefinition> listTools() {
        if (!isConnected()) {
            throw new McpOperationException(null, "Not connected to MCP server");
        }
        
        try {
            McpSchema.ListToolsResult result = sdkClient.listTools();
            List<McpToolDefinition> tools = new ArrayList<>();
            
            for (McpSchema.Tool tool : result.tools()) {
                // Convert inputSchema to JsonNode
                JsonNode inputSchema = convertInputSchema(tool.inputSchema());
                tools.add(new McpToolDefinition(
                    tool.name(),
                    tool.description(),
                    inputSchema
                ));
            }
            
            return tools;
        } catch (Exception e) {
            throw new McpOperationException(null, "Failed to list tools", e);
        }
    }
    
    private JsonNode convertInputSchema(Object schema) {
        if (schema == null) {
            return null;
        }
        try {
            // The schema might be JsonSchema, Map, or String depending on SDK version
            return MAPPER.valueToTree(schema);
        } catch (Exception e) {
            return null;
        }
    }
    
    @Override
    public ToolResult executeTool(String toolName, Map<String, Object> params) {
        if (!isConnected()) {
            throw new McpOperationException(toolName, "Not connected to MCP server");
        }
        
        try {
            McpSchema.CallToolResult result = sdkClient.callTool(
                new McpSchema.CallToolRequest(toolName, params)
            );
            
            StringBuilder content = new StringBuilder();
            for (McpSchema.Content item : result.content()) {
                if (item instanceof McpSchema.TextContent textContent) {
                    content.append(textContent.text());
                }
            }
            
            return ToolResult.success(content.toString());
        } catch (Exception e) {
            throw new McpOperationException(toolName, "Failed to execute tool", e);
        }
    }
    
    @Override
    public boolean isConnected() {
        return sdkClient != null;
    }
    
    @Override
    public String getServerName() {
        return serverName;
    }
}