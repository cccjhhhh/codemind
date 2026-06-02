package com.codemind.impl.safety;

import com.codemind.api.safety.Permission;

import java.util.HashSet;
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
    private final Set<Permission> sessionPermissions = new HashSet<>();
    private final Set<Permission> allowedPermissions = new HashSet<>();
    
    public PermissionGate(Set<String> allowedCommands, boolean confirmDangerousOperations) {
        this.allowedCommands = allowedCommands;
        this.confirmDangerousOperations = confirmDangerousOperations;
    }
    
    public PermissionGate(boolean confirmDangerousOperations) {
        this(Set.of(), confirmDangerousOperations);
    }
    
    /**
     * 检查权限
     */
    public boolean hasPermission(Permission permission) {
        // 检查会话权限
        if (sessionPermissions.contains(permission)) {
            return true;
        }
        
        // 检查永久授权
        if (allowedPermissions.contains(permission)) {
            return true;
        }
        
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
     * 检查是否需要确认
     */
    public boolean needsConfirmation(Permission permission) {
        // 会话权限不需要确认
        if (sessionPermissions.contains(permission)) {
            return false;
        }
        
        // 已授权的权限不需要确认
        if (allowedPermissions.contains(permission)) {
            return false;
        }
        
        switch (permission) {
            case READ_FILE:
                return false; // 读取文件不需要确认
            case WRITE_FILE:
            case EXECUTE_COMMAND:
                return confirmDangerousOperations && !isAllowed(permission);
            default:
                return true;
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
     * 授予会话级别的权限（本次会话有效）
     */
    public void grantSessionPermission(Permission permission) {
        sessionPermissions.add(permission);
    }
    
    /**
     * 授予权限（永久）
     */
    public void allow(Permission permission) {
        allowedPermissions.add(permission);
    }
    
    /**
     * 撤销权限
     */
    public void disallow(Permission permission) {
        allowedPermissions.remove(permission);
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
        // 检查白名单
        return allowedPermissions.contains(permission) ||
               (allowedCommands != null && !allowedCommands.isEmpty());
    }
}