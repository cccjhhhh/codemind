package com.codemind.mcp;

import com.codemind.mcp.McpClient;
import com.codemind.mcp.McpClientFactory;
import com.codemind.mcp.McpServerConfig;

/**
 * MCP 客户端工厂实现
 * 
 * 负责创建 McpClient 实例。
 */
public class McpClientFactoryImpl implements McpClientFactory {
    
    private final McpTransportFactory transportFactory;
    
    public McpClientFactoryImpl() {
        this.transportFactory = new McpTransportFactory();
    }
    
    @Override
    public McpClient createClient(McpServerConfig config) {
        return new McpClientImpl(transportFactory);
    }
}
