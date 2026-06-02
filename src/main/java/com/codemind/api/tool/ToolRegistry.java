package com.codemind.api.tool;

import com.codemind.api.llm.ToolDefinition;
import java.util.List;
import java.util.Map;

/**
 * 工具注册中心接口
 */
public interface ToolRegistry {
    
    /**
     * 注册工具
     */
    void register(Tool tool);
    
    /**
     * 注销工具
     */
    void unregister(String name);
    
    /**
     * 获取工具
     */
    Tool get(String name);
    
    /**
     * 执行工具（带权限检查）
     */
    ToolResult execute(String name, Map<String, Object> params);
    
    /**
     * 获取工具定义（用于 LLM Function Calling）
     */
    ToolDefinition getDefinition(String name);
    
    /**
     * 获取所有工具定义
     */
    List<ToolDefinition> getAllDefinitions();
    
    /**
     * 检查工具是否存在
     */
    boolean hasTool(String name);
}