package com.codemind.tool;

import com.codemind.llm.ToolDefinition;
import com.codemind.safety.PermissionGate;
import com.codemind.tool.spi.Tool;
import com.codemind.tool.spi.ToolHook;
import com.codemind.tool.ToolRegistry;
import com.codemind.tool.ToolResult;
import com.codemind.skill.SkillDefinition;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class ToolRegistryImpl implements ToolRegistry {

    private static final Logger log = LoggerFactory.getLogger(ToolRegistryImpl.class);
    private final Map<String, Tool> tools = new ConcurrentHashMap<>();
    private final Map<String, String> deprecatedToCanonical = new ConcurrentHashMap<>();
    private final List<ToolHook> hooks = new CopyOnWriteArrayList<>();
    private final PermissionGate permissionGate;

    public ToolRegistryImpl(PermissionGate permissionGate) {
        this.permissionGate = permissionGate;
    }

    @Override
    public void register(Tool tool) {
        tools.put(tool.getName(), tool);
        tool.getDeprecatedName().ifPresent(deprecatedName -> {
            deprecatedToCanonical.put(deprecatedName, tool.getName());
            tools.put(deprecatedName, tool);
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
        if (tool == null) return null;
        return new ToolDefinition(tool.getName(), tool.getDescription(), tool.getInputSchema());
    }

    private Tool resolveTool(String name) {
        Tool tool = tools.get(name);
        if (tool != null) return tool;
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
    public List<ToolDefinition> getDefinitionsForSkill(SkillDefinition skill) {
        if (skill == null) return getAllDefinitions();
        List<String> allowed = skill.getAllowedTools();
        if (allowed == null || allowed.isEmpty()) return getAllDefinitions();
        Set<String> allowedSet = new HashSet<>(allowed);
        return tools.values().stream()
            .distinct()
            .filter(t -> allowedSet.contains(t.getName()))
            .map(t -> new ToolDefinition(t.getName(), t.getDescription(), t.getInputSchema()))
            .toList();
    }

    @Override
    public void registerHook(ToolHook hook) {
        hooks.add(hook);
    }

    @Override
    public void removeHook(String hookName) {
        hooks.removeIf(h -> h.getClass().getSimpleName().equals(hookName));
    }

    @Override
    public ToolResult execute(String name, Map<String, Object> params) {
        Tool tool = resolveTool(name);
        if (tool == null) {
            return ToolResult.failure("Tool not found: " + name);
        }

        // 1. 运行所有 pre-hooks
        for (ToolHook hook : hooks) {
            try {
                hook.preExecute(name, params);
            } catch (Exception e) {
                return ToolResult.failure("Hook 拒绝执行: " + e.getMessage());
            }
        }

        // 2. 执行工具
        long start = System.nanoTime();
        ToolResult result;
        try {
            result = tool.execute(params);
        } catch (Exception e) {
            result = ToolResult.failure("Tool execution failed: " + e.getMessage());
        }
        long elapsedMs = (System.nanoTime() - start) / 1_000_000;

        // 3. 运行所有 post-hooks（逆序，洋葱模型）
        for (int i = hooks.size() - 1; i >= 0; i--) {
            try {
                hooks.get(i).postExecute(name, result, elapsedMs);
            } catch (Exception e) {
                log.warn("Hook postExecute 异常: {}", e.getMessage());
            }
        }

        return result;
    }

    @Override
    public boolean hasTool(String name) {
        return resolveTool(name) != null;
    }

    public Set<String> getToolNames() {
        return Collections.unmodifiableSet(tools.keySet());
    }
}
