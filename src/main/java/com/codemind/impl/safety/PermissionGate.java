package com.codemind.impl.safety;

import java.util.Set;

/**
 * 权限网关
 * 
 * 控制危险操作的执行权限。
 * 学习要点：权限控制模型、白名单机制、用户确认流程
 */
public class PermissionGate {
    
    private final Set<String> allowedCommands;
    private final boolean confirmDangerousOperations;
    
    public PermissionGate(Set<String> allowedCommands, boolean confirmDangerousOperations) {
        this.allowedCommands = allowedCommands;
        this.confirmDangerousOperations = confirmDangerousOperations;
    }
    
    /**
     * 检查权限
     */
    public boolean hasPermission(Permission permission) {
        switch (permission) {
            case READ_FILE:
                return true; // 读取文件默认允许
            case WRITE_FILE:
            case EXECUTE_COMMAND:
                return !confirmDangerousOperations || isAllowed(permission);
            default:
                return false;
        }
    }
    
    /**
     * 请求权限（对于需要确认的操作）
     */
    public boolean requestPermission(Permission permission, String context) {
        // TODO: 实现用户交互确认
        // 在 CLI 模式下，应该询问用户
        return isAllowed(permission);
    }
    
    /**
     * 强制检查权限，不满足则抛出异常
     */
    public void enforcePermission(Permission permission, String context) {
        if (!hasPermission(permission)) {
            throw new SecurityException(
                "权限不足: " + permission.getDescription() + 
                (context != null ? " (上下文: " + context + ")" : "")
            );
        }
    }
    
    private boolean isAllowed(Permission permission) {
        // TODO: 检查白名单
        return allowedCommands != null && !allowedCommands.isEmpty();
    }
}