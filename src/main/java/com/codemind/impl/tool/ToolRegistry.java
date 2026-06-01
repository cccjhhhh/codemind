package com.codemind.impl.tool;

import com.codemind.api.llm.ToolDefinition;
import com.codemind.api.tool.Tool;
import com.codemind.api.tool.ToolResult;
import com.codemind.impl.safety.Permission;
import com.codemind.impl.safety.PermissionGate;

import java.util.*;

/**
 * 工具注册中心
 * 
 * 管理所有可用工具的定义和执行。
 * 学习要点：工具注册与发现、参数验证、执行调度、权限控制
 */
public class ToolRegistry {
    
    /** 工具名称到权限的映射 */
    private static final Map<String, Permission> TOOL_PERMISSIONS = Map.of(
        "read_file", Permission.READ_FILE,
        "write_file", Permission.WRITE_FILE,
        "execute_command", Permission.EXECUTE_COMMAND,
        "search_code", Permission.READ_FILE,
        "parse_logs", Permission.READ_FILE
    );
    
    private final Map<String, Tool> tools = new HashMap<>();
    private final PermissionGate permissionGate;
    
    public ToolRegistry() {
        this.permissionGate = new PermissionGate(true); // 默认需要确认危险操作
    }
    
    public ToolRegistry(PermissionGate permissionGate) {
        this.permissionGate = permissionGate;
    }
    
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
     * 执行工具（带权限检查）
     * 
     * @return 如果权限不足，返回失败结果（不执行）
     */
    public ToolResult execute(String name, Map<String, Object> params) {
        Tool tool = tools.get(name);
        if (tool == null) {
            return ToolResult.failure("Tool not found: " + name);
        }
        
        // 检查权限
        Permission permission = TOOL_PERMISSIONS.get(name);
        if (permission != null && !permissionGate.hasPermission(permission)) {
            return ToolResult.failure(
                "权限不足: " + permission.getDescription() + "，请在设置中授权"
            );
        }
        
        try {
            return tool.execute(params);
        } catch (Exception e) {
            return ToolResult.failure("Tool execution failed: " + e.getMessage());
        }
    }
    
    /**
     * 检查工具执行是否需要用户确认
     */
    public boolean requiresConfirmation(String toolName) {
        Permission permission = TOOL_PERMISSIONS.get(toolName);
        if (permission == null) {
            return false;
        }
        return permissionGate.requiresConfirmation(permission);
    }
    
    /**
     * 授予工具执行权限（添加到白名单）
     */
    public void grantPermission(Permission permission) {
        permissionGate.allow(permission);
    }
    
    /**
     * 撤销工具执行权限
     */
    public void revokePermission(Permission permission) {
        permissionGate.disallow(permission);
    }
    
    /**
     * 获取权限 Gate（用于 CLI 询问用户）
     */
    public PermissionGate getPermissionGate() {
        return permissionGate;
    }
    
    /**
     * 获取某个工具需要的权限
     */
    public Permission getToolPermission(String toolName) {
        return TOOL_PERMISSIONS.get(toolName);
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