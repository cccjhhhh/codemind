package com.codemind.mcp;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.Map;

/**
 * MCP 服务器配置
 */
public class McpServerConfig {
    
    /**
     * 传输方式：stdio 或 http-sse
     */
    private String transport;
    
    /**
     * stdio 模式：命令
     */
    private String command;
    
    /**
     * stdio 模式：命令参数
     */
    private String[] args;
    
    /**
     * http-sse 模式：服务器 URL
     */
    private String url;
    
    /**
     * http-sse 模式：请求头
     */
    private Map<String, String> headers;
    
    /**
     * 是否启用
     */
    @JsonProperty("enabled")
    private boolean enabled = true;
    
    // Getters and Setters
    
    public String getTransport() {
        return transport;
    }
    
    public void setTransport(String transport) {
        this.transport = transport;
    }
    
    public String getCommand() {
        return command;
    }
    
    public void setCommand(String command) {
        this.command = command;
    }
    
    public String[] getArgs() {
        return args;
    }
    
    public void setArgs(String[] args) {
        this.args = args;
    }
    
    public String getUrl() {
        return url;
    }
    
    public void setUrl(String url) {
        this.url = url;
    }
    
    public Map<String, String> getHeaders() {
        return headers;
    }
    
    public void setHeaders(Map<String, String> headers) {
        this.headers = headers;
    }
    
    public boolean isEnabled() {
        return enabled;
    }
    
    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }
}
