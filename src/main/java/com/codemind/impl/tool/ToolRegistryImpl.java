package com.codemind.impl.tool;

import com.codemind.api.llm.ToolDefinition;
import com.codemind.api.tool.Tool;
import com.codemind.api.tool.ToolResult;
import com.codemind.api.tool.ToolRegistry;
import com.codemind.api.safety.Permission;
import com.codemind.impl.safety.PermissionGate;

import java.util.*;

/**
 * 工具注册中心实现
 * 
 * 管理所有可用工具的定义和执行。
 * 学习要点：工具注册与发现、参数验证、执行调度、权限控制
 */
public class ToolRegistryImpl implements ToolRegistry {
    
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
    
    public ToolRegistryImpl() {
        this.permissionGate = new PermissionGate(true); // 默认需要确认危险操作
    }
    
    public ToolRegistryImpl(PermissionGate permissionGate) {
        this.permissionGate = permissionGate;
    }
    
    @Override
    public void register(Tool tool) {
        tools.put(tool.getName(), tool);
    }
    
    @Override
    public void unregister(String name) {
        tools.remove(name);
    }
    
    @Override
    public Tool get(String name) {
        return tools.get(name);
    }
    
    @Override
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
    
    @Override
    public List<ToolDefinition> getAllDefinitions() {
        return tools.values().stream()
            .map(t -> new ToolDefinition(t.getName(), t.getDescription(), t.getInputSchema()))
            .toList();
    }
    
@Override
    public ToolResult execute(String name, Map<String, Object> params) {
        Tool tool = tools.get(name);
        if (tool == null) {
            return ToolResult.failure("Tool not found: " + name);
        }
        
        // 检查是否需要权限确认
        Permission permission = TOOL_PERMISSIONS.get(name);
        if (permission != null && permissionGate.needsConfirmation(permission)) {
            return ToolResult.needsConfirmation(permission, "工具: " + name);
        }
        
        try {
            return tool.execute(params);
        } catch (Exception e) {
            return ToolResult.failure("Tool execution failed: " + e.getMessage());
        }
    }
    
    @Override
    public boolean hasTool(String name) {
        return tools.containsKey(name);
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
     * 获取所有工具名称
     */
    public Set<String> getToolNames() {
        return Collections.unmodifiableSet(tools.keySet());
    }
}