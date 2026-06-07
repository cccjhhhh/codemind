package com.codemind.api.safety;

/**
 * 权限级别枚举
 * 
 * 定义工具执行权限的三层模型：
 * - ALLOW: 自动允许执行，无需用户确认
 * - ASK: 需要用户确认后执行
 * - DENY: 禁止执行
 * 
 * 参考设计：Claude Code Permission Model
 */
public enum PermissionLevel {
    /**
     * 自动允许 - 工具可以直接执行，无需确认
     * 适用：只读操作（Read, Grep, Glob, WebFetch）
     */
    ALLOW("自动允许"),
    
    /**
     * 询问用户 - 需要用户确认后执行
     * 适用：修改操作（Write, Edit, Bash）
     */
    ASK("询问用户"),
    
    /**
     * 禁止执行 - 不允许执行
     * 适用：危险操作（Agent）
     */
    DENY("禁止执行");
    
    private final String description;
    
    PermissionLevel(String description) {
        this.description = description;
    }
    
    public String getDescription() {
        return description;
    }
}
