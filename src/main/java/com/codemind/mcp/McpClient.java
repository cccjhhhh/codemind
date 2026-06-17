package com.codemind.mcp;

import com.codemind.tool.ToolResult;
import java.util.List;
import java.util.Map;

/**
 * MCP 客户端接口
 * 
 * 负责与 MCP 服务器通信，管理连接生命周期。
 */
public interface McpClient {
    
    /**
     * 连接到 MCP 服务器
     *
     * @param config 服务器配置
     */
    void connect(McpServerConfig config);

    /**
     * 断开连接
     */
    void disconnect();

    /**
     * 获取可用工具列表
     *
     * @return 工具定义列表
     */
    List<McpToolDefinition> listTools();

    /**
     * 执行工具
     *
     * @param toolName 工具名称
     * @param params 输入参数
     * @return 执行结果
     */
    ToolResult executeTool(String toolName, Map<String, Object> params);
    
    /**
     * 检查连接状态
     * 
     * @return 是否已连接
     */
    boolean isConnected();
    
    /**
     * 获取服务器名称
     * 
     * @return 服务器名称
     */
    String getServerName();
}
