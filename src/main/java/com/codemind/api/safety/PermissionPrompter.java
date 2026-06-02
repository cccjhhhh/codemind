package com.codemind.api.safety;

/**
 * 权限确认接口
 * 用于在工具执行过程中询问用户是否授权危险操作
 */
public interface PermissionPrompter {
    
    /**
     * 请求用户确认权限
     * 
     * @param permission 需要的权限类型
     * @param context 权限请求的上下文（如工具名、参数摘要）
     * @return 用户的决策（ALLOW / DENY / ALLOW_SESSION）
     */
    PermissionDecision prompt(Permission permission, String context);
}
