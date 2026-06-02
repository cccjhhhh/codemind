package com.codemind.api.safety;

/**
 * 用户对权限请求的决策
 */
public enum PermissionDecision {
    /** 允许本次操作 */
    ALLOW,
    /** 拒绝操作 */
    DENY,
    /** 允许本次会话内所有同类操作 */
    ALLOW_SESSION
}
