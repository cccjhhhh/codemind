package com.codemind.impl.safety;

import com.codemind.api.safety.PermissionGate;
import com.codemind.api.safety.PermissionLevel;
import com.codemind.api.safety.PermissionPrompter;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class PermissionGateImpl implements PermissionGate {

    private final PermissionPrompter permissionPrompter;
    private final Map<String, PermissionLevel> runtimeLevels = new ConcurrentHashMap<>();
    private List<PermissionRule> rules = List.of();

    private static final Map<String, PermissionLevel> DEFAULT_LEVELS = Map.ofEntries(
        Map.entry("Read", PermissionLevel.ALLOW),
        Map.entry("Write", PermissionLevel.ASK),
        Map.entry("Edit", PermissionLevel.ASK),
        Map.entry("Glob", PermissionLevel.ALLOW),
        Map.entry("Grep", PermissionLevel.ALLOW),
        Map.entry("Bash", PermissionLevel.ASK),
        Map.entry("WebFetch", PermissionLevel.ALLOW),
        Map.entry("LoadSkill", PermissionLevel.ALLOW),
        Map.entry("Todo", PermissionLevel.ALLOW),
        Map.entry("Task", PermissionLevel.ALLOW)
    );

    public PermissionGateImpl(PermissionPrompter permissionPrompter) {
        this.permissionPrompter = permissionPrompter;
    }

    public record PermissionRule(String tool, String condition, PermissionLevel level) {
        public boolean matches(String toolName) {
            return "*".equals(tool) || tool.equals(toolName);
        }
    }

    public void setRules(List<PermissionRule> rules) {
        this.rules = rules != null ? rules : List.of();
    }

    @Override
    public PermissionLevel getDefaultLevel(String toolName) {
        // 1. 运行时覆盖优先
        PermissionLevel runtime = runtimeLevels.get(toolName);
        if (runtime != null) return runtime;

        // 2. 规则匹配
        for (PermissionRule rule : rules) {
            if (rule.matches(toolName)) return rule.level();
        }

        // 3. 静态默认
        return DEFAULT_LEVELS.getOrDefault(toolName, PermissionLevel.ASK);
    }

    @Override
    public void setDefaultLevel(String toolName, PermissionLevel level) {
        runtimeLevels.put(toolName, level);
    }

    @Override
    public boolean requestPermission(String toolName, String context) {
        if (permissionPrompter == null) return false;
        PermissionPrompter.Decision decision = permissionPrompter.prompt(toolName, context);
        return decision == PermissionPrompter.Decision.ALLOW
            || decision == PermissionPrompter.Decision.ALLOW_SESSION;
    }
}
