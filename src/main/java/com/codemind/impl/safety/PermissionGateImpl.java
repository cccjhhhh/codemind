package com.codemind.impl.safety;

import com.codemind.api.safety.Permission;
import com.codemind.api.safety.PermissionDecision;
import com.codemind.api.safety.PermissionGate;
import com.codemind.api.safety.PermissionPrompter;

import java.util.HashSet;
import java.util.Set;

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
    
    private final Set<String> allowedCommands;
    private final boolean confirmDangerousOperations;
    private final PermissionPrompter permissionPrompter;
    private final Set<Permission> sessionPermissions = new HashSet<>();
    private final Set<Permission> allowedPermissions = new HashSet<>();
    
    /**
     * 完整构造器
     * 
     * @param allowedCommands 命令白名单
     * @param confirmDangerousOperations 是否确认危险操作
     * @param permissionPrompter 权限询问器（用于用户交互）
     */
    public PermissionGateImpl(Set<String> allowedCommands, boolean confirmDangerousOperations, PermissionPrompter permissionPrompter) {
        this.allowedCommands = allowedCommands;
        this.confirmDangerousOperations = confirmDangerousOperations;
        this.permissionPrompter = permissionPrompter;
    }
    
    /**
     * 简化构造器（无命令白名单）
     */
    public PermissionGateImpl(boolean confirmDangerousOperations, PermissionPrompter permissionPrompter) {
        this(Set.of(), confirmDangerousOperations, permissionPrompter);
    }
    
    /**
     * 向后兼容构造器（无用户交互，自动拒绝危险操作）
     * 
     * @deprecated 建议使用带 PermissionPrompter 的构造器
     */
    @Deprecated
    public PermissionGateImpl(Set<String> allowedCommands, boolean confirmDangerousOperations) {
        this.allowedCommands = allowedCommands;
        this.confirmDangerousOperations = confirmDangerousOperations;
        this.permissionPrompter = null;
    }
    
    /**
     * 向后兼容构造器
     * 
     * @deprecated 建议使用带 PermissionPrompter 的构造器
     */
    @Deprecated
    public PermissionGateImpl(boolean confirmDangerousOperations) {
        this(Set.of(), confirmDangerousOperations, null);
    }
    
    @Override
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
    
    @Override
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
     * 请求用户授权
     * 
     * 通过 PermissionPrompter 与用户交互，获取授权决策。
     * 
     * 决策处理：
     * - ALLOW_SESSION: 自动授予会话权限，下次无需确认
     * - ALLOW: 仅允许本次操作
     * - DENY: 拒绝操作
     */
    @Override
    public boolean requestPermission(Permission permission, String context) {
        // 如果没有配置权限询问器（向后兼容），使用 isAllowed 判断
        if (permissionPrompter == null) {
            return isAllowed(permission);
        }
        
        // 获取用户决策
        PermissionDecision decision = permissionPrompter.prompt(permission, context);
        
        switch (decision) {
            case ALLOW_SESSION:
                // 授予会话权限，下次无需确认
                grantSessionPermission(permission);
                return true;
            case ALLOW:
                return true;
            case DENY:
            default:
                return false;
        }
    }
    
    @Override
    public void grantSessionPermission(Permission permission) {
        sessionPermissions.add(permission);
    }
    
    @Override
    public void revokeSessionPermission(Permission permission) {
        sessionPermissions.remove(permission);
    }
    
    @Override
    public void allow(Permission permission) {
        allowedPermissions.add(permission);
    }
    
    @Override
    public void disallow(Permission permission) {
        allowedPermissions.remove(permission);
    }
    
    @Override
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