package com.codemind.mcp;

import io.modelcontextprotocol.client.transport.HttpClientStreamableHttpTransport;
import io.modelcontextprotocol.client.transport.ServerParameters;
import io.modelcontextprotocol.client.transport.StdioClientTransport;
import io.modelcontextprotocol.json.jackson3.JacksonMcpJsonMapper;
import io.modelcontextprotocol.spec.McpClientTransport;
import tools.jackson.databind.json.JsonMapper;

/**
 * MCP 传输工厂
 * 
 * 根据配置创建相应的传输实例。
 */
public class McpTransportFactory {
    
    /**
     * 根据配置创建传输实例
     * 
     * @param config 服务器配置
     * @return 传输实例
     * @throws IllegalArgumentException 不支持的传输类型
     */
    public McpClientTransport createTransport(McpServerConfig config) {
        String transportType = config.getTransport();
        
        if ("stdio".equals(transportType)) {
            return createStdioTransport(config);
        } else if ("http-sse".equals(transportType)) {
            return createHttpTransport(config);
        } else {
            throw new IllegalArgumentException("Unsupported transport type: " + transportType);
        }
    }
    
    private McpClientTransport createStdioTransport(McpServerConfig config) {
        ServerParameters.Builder paramsBuilder = ServerParameters.builder(config.getCommand());
        
        if (config.getArgs() != null) {
            paramsBuilder.args(java.util.Arrays.asList(config.getArgs()));
        }
        
        return new StdioClientTransport(paramsBuilder.build(), new JacksonMcpJsonMapper(JsonMapper.shared()));
    }
    
    private McpClientTransport createHttpTransport(McpServerConfig config) {
        if (config.getUrl() == null || config.getUrl().isEmpty()) {
            throw new IllegalArgumentException("URL is required for http-sse transport");
        }
        
        HttpClientStreamableHttpTransport.Builder builder = HttpClientStreamableHttpTransport
            .builder(config.getUrl());
        
        if (config.getHeaders() != null) {
            builder.customizeRequest(request -> 
                config.getHeaders().forEach(request::setHeader));
        }
        
        return builder.build();
    }
}