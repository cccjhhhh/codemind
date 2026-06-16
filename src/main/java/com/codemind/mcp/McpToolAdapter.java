package com.codemind.mcp;

import com.codemind.tool.spi.Tool;

/**
 * MCP 工具适配器接口
 * 
 * 将 MCP 工具定义转换为 CodeMind Tool 接口。
 */
public interface McpToolAdapter {
    
    /**
     * 将 MCP 工具转换为 CodeMind Tool
     * 
     * @param mcpTool MCP 工具定义
     * @param client MCP 客户端实例
     * @return CodeMind Tool 实现
     */
    Tool adapt(McpToolDefinition mcpTool, McpClient client);
    
    /**
     * 生成带前缀的工具名称
     * 
     * @param serverName 服务器名称
     * @param toolName 原始工具名称
     * @return 带前缀的工具名称（如 mcp_filesystem_read）
     */
    String generatePrefixedName(String serverName, String toolName);
}
