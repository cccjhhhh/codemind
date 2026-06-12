package com.codemind.api.mcp;

/**
 * MCP 连接异常
 * 
 * 当连接 MCP 服务器失败时抛出。
 */
public class McpConnectionException extends Exception {
    
    private final String serverName;
    
    public McpConnectionException(String serverName, String message) {
        super(message);
        this.serverName = serverName;
    }
    
    public McpConnectionException(String serverName, String message, Throwable cause) {
        super(message, cause);
        this.serverName = serverName;
    }
    
    public String getServerName() {
        return serverName;
    }
}
