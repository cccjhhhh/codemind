package com.codemind.api.mcp;

/**
 * MCP 操作异常
 * 
 * 当 MCP 操作失败时抛出。
 */
public class McpOperationException extends Exception {
    
    private final String toolName;
    
    public McpOperationException(String toolName, String message) {
        super(message);
        this.toolName = toolName;
    }
    
    public McpOperationException(String toolName, String message, Throwable cause) {
        super(message, cause);
        this.toolName = toolName;
    }
    
    public String getToolName() {
        return toolName;
    }
}
