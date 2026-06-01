package com.codemind.impl.tool;

import com.codemind.api.llm.ToolDefinition;
import com.codemind.api.tool.Tool;
import com.codemind.api.tool.ToolResult;
import java.util.*;

/**
 * 工具注册中心
 * 
 * 管理所有可用工具的定义和执行。
 * 学习要点：工具注册与发现、参数验证、执行调度
 */
public class ToolRegistry {
    
    private final Map<String, Tool> tools = new HashMap<>();
    
    /**
     * 注册工具
     */
    public void register(Tool tool) {
        tools.put(tool.getName(), tool);
    }
    
    /**
     * 获取工具定义（用于 LLM Function Calling）
     */
    public ToolDefinition getDefinition(String name) {
        Tool tool = tools.get(name);
        if (tool == null) {
            return null;
        }
        return new ToolDefinition(
            tool.getName(),
            tool.getDescription(),
            tool.getInputSchema()
        );
    }
    
    /**
     * 获取所有工具定义
     */
    public List<ToolDefinition> getAllDefinitions() {
        return tools.values().stream()
            .map(t -> new ToolDefinition(t.getName(), t.getDescription(), t.getInputSchema()))
            .toList();
    }
    
    /**
     * 执行工具
     */
    public ToolResult execute(String name, Map<String, Object> params) {
        Tool tool = tools.get(name);
        if (tool == null) {
            return ToolResult.failure("Tool not found: " + name);
        }
        try {
            return tool.execute(params);
        } catch (Exception e) {
            return ToolResult.failure("Tool execution failed: " + e.getMessage());
        }
    }
    
    /**
     * 检查工具是否存在
     */
    public boolean hasTool(String name) {
        return tools.containsKey(name);
    }
    
    /**
     * 获取所有工具名称
     */
    public Set<String> getToolNames() {
        return Collections.unmodifiableSet(tools.keySet());
    }
}