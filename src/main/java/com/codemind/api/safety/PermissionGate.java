package com.codemind.api.safety;

/**
 * 权限网关接口
 * 
 * 控制危险操作的执行权限。
 * 学习要点：权限控制模型、白名单机制、用户确认流程
 * 
 * 设计原则：依赖倒置原则（DIP）
 * - 高层模块依赖此接口，而非具体实现
 * - 实现类可以有不同的策略（CLI确认、配置文件、自动允许等）
 */
public interface PermissionGate {
    
    /**
     * 检查是否拥有指定权限
     * 
     * @param permission 要检查的权限
     * @return true 如果拥有权限
     */
    boolean hasPermission(Permission permission);
    
    /**
     * 检查操作是否需要用户确认
     * 
     * @param permission 要检查的权限
     * @return true 如果需要确认
     */
    boolean needsConfirmation(Permission permission);
    
    /**
     * 授予会话级别的权限（本次会话有效）
     * 
     * @param permission 要授予的权限
     */
    void grantSessionPermission(Permission permission);
    
    /**
     * 撤销会话权限
     * 
     * @param permission 要撤销的权限
     */
    void revokeSessionPermission(Permission permission);
    
    /**
     * 授予权限（永久）
     * 
     * @param permission 要授予的权限
     */
    void allow(Permission permission);
    
    /**
     * 撤销权限
     * 
     * @param permission 要撤销的权限
     */
    void disallow(Permission permission);
    
    /**
     * 强制检查权限，不满足则抛出异常
     * 
     * @param permission 要检查的权限
     * @param context 操作上下文（用于错误提示）
     * @throws SecurityException 如果权限不足
     */
    void enforcePermission(Permission permission, String context);
}