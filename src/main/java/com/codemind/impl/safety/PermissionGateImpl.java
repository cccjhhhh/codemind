package com.codemind.impl.safety;

import com.codemind.api.safety.*;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 权限网关实现
 *
 * 控制危险操作的执行权限。
 * 学习要点：权限控制模型、白名单机制、用户确认流程
 *
 * 设计原则：依赖倒置原则（DIP）
 * - 此类实现 PermissionGate 接口
 * - 高层模块通过接口使用，不直接依赖此类
 * - PermissionPrompter 通过构造器注入
 */
public class PermissionGateImpl implements PermissionGate {

    private final PermissionPrompter permissionPrompter;
    private final Map<String, PermissionLevel> runtimeLevels = new ConcurrentHashMap<>();

    // 默认权限级别映射
    private static final Map<String, PermissionLevel> DEFAULT_LEVELS = Map.ofEntries(
        Map.entry("Read", PermissionLevel.ALLOW),
        Map.entry("Write", PermissionLevel.ASK),
        Map.entry("Edit", PermissionLevel.ASK),
        Map.entry("Glob", PermissionLevel.ALLOW),
        Map.entry("Grep", PermissionLevel.ALLOW),
        Map.entry("Bash", PermissionLevel.ASK),
        Map.entry("WebFetch", PermissionLevel.ALLOW)
    );

    public PermissionGateImpl(PermissionPrompter permissionPrompter) {
        this.permissionPrompter = permissionPrompter;
    }

    @Override
    public PermissionLevel getDefaultLevel(String toolName) {
        return runtimeLevels.getOrDefault(toolName,
            DEFAULT_LEVELS.getOrDefault(toolName, PermissionLevel.ASK));
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
