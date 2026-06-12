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
    ALLOW,
    ASK,
    DENY;
}
