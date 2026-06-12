package com.codemind.api.mcp;

/**
 * MCP 客户端工厂接口
 * 
 * 负责创建 McpClient 实例。
 */
public interface McpClientFactory {
    
    /**
     * 根据配置创建 MCP 客户端
     * 
     * @param config 服务器配置
     * @return MCP 客户端实例
     */
    McpClient createClient(McpServerConfig config);
}
