package com.codemind.impl.tool;

import com.codemind.api.llm.ToolDefinition;
import com.codemind.api.safety.PermissionGate;
import com.codemind.api.safety.PermissionLevel;
import com.codemind.api.tool.Tool;
import com.codemind.api.tool.ToolRegistry;
import com.codemind.api.tool.ToolResult;

import java.util.*;

/**
 * 工具注册中心实现
 *
 * 管理所有可用工具的定义和执行。
 */
public class ToolRegistryImpl implements ToolRegistry {

    /** 工具名称到工具实例的映射 */
    private final Map<String, Tool> tools = new HashMap<>();
    /** 废弃名称到主名称的映射（用于向后兼容） */
    private final Map<String, String> deprecatedToCanonical = new HashMap<>();

    private final PermissionGate permissionGate;

    public ToolRegistryImpl(PermissionGate permissionGate) {
        this.permissionGate = permissionGate;
    }

    @Override
    public void register(Tool tool) {
        // 注册主名称
        tools.put(tool.getName(), tool);

        // 注册 deprecated alias
        tool.getDeprecatedName().ifPresent(deprecatedName -> {
            deprecatedToCanonical.put(deprecatedName, tool.getName());
            tools.put(deprecatedName, tool);  // 同时用旧名注册，方便查找
        });
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
        Tool tool = resolveTool(name);
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
     * 解析工具名称，支持 deprecated alias
     */
    private Tool resolveTool(String name) {
        Tool tool = tools.get(name);
        if (tool != null) {
            return tool;
        }
        // 尝试通过 deprecated 映射解析
        String canonicalName = deprecatedToCanonical.get(name);
        return canonicalName != null ? tools.get(canonicalName) : null;
    }

    @Override
    public List<ToolDefinition> getAllDefinitions() {
        return tools.values().stream()
            .distinct()
            .map(t -> new ToolDefinition(t.getName(), t.getDescription(), t.getInputSchema()))
            .toList();
    }

    @Override
    public ToolResult execute(String name, Map<String, Object> params) {
        Tool tool = tools.get(name);
        if (tool == null) {
            return ToolResult.failure("Tool not found: " + name);
        }

        // 检查 PermissionGate 的运行时覆盖，再回退到工具自身默认
        PermissionLevel level = PermissionLevel.ASK;
        if (permissionGate != null) {
            level = permissionGate.getDefaultLevel(name);
        }
        if (level == PermissionLevel.DENY) {
            return ToolResult.failure("Tool " + tool.getName() + " is denied by default");
        }

        if (level == PermissionLevel.ASK) {
            if (permissionGate != null) {
                boolean granted = permissionGate.requestPermission(name,
                    "工具 " + name + " 请求执行: " + params);
                if (!granted) {
                    return ToolResult.failure("用户拒绝授权: " + name);
                }
            }
        }

        try {
            return tool.execute(params);
        } catch (Exception e) {
            return ToolResult.failure("Tool execution failed: " + e.getMessage());
        }
    }

    @Override
    public boolean hasTool(String name) {
        return resolveTool(name) != null;
    }

    /**
     * 获取所有工具名称
     */
    public Set<String> getToolNames() {
        return Collections.unmodifiableSet(tools.keySet());
    }
}
