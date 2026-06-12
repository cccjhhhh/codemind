package com.codemind.impl.mcp;

import com.codemind.api.tool.Tool;
import com.codemind.api.tool.ToolResult;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * MCP 工具注册表
 * 
 * 管理所有 MCP 工具的注册和查询。
 * 与原生 ToolRegistry 独立。
 */
public class McpToolRegistry {

    private final Map<String, Tool> tools = new ConcurrentHashMap<>();
    private final Map<String, List<String>> serverTools = new ConcurrentHashMap<>();

    public void registerServerTools(String serverName, List<Tool> tools) {
        List<String> toolNames = new ArrayList<>();
        for (Tool tool : tools) {
            this.tools.put(tool.getName(), tool);
            toolNames.add(tool.getName());
        }
        serverTools.put(serverName, toolNames);
    }

    public void unregisterServerTools(String serverName) {
        List<String> toolNames = serverTools.remove(serverName);
        if (toolNames != null) {
            for (String toolName : toolNames) {
                tools.remove(toolName);
            }
        }
    }

    public List<Tool> getAllTools() {
        return new ArrayList<>(tools.values());
    }

    public Tool getTool(String prefixedName) {
        return tools.get(prefixedName);
    }

    public ToolResult executeTool(String prefixedName, Map<String, Object> params) {
        Tool tool = tools.get(prefixedName);
        if (tool == null) {
            return ToolResult.failure("MCP tool not found: " + prefixedName);
        }
        return tool.execute(params);
    }

    public boolean hasTool(String prefixedName) {
        return tools.containsKey(prefixedName);
    }
}
