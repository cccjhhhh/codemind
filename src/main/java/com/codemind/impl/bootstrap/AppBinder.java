package com.codemind.impl.bootstrap;

import com.codemind.api.safety.PermissionGate;
import com.codemind.api.session.SessionManager;
import com.codemind.api.skill.SkillRegistry;
import com.codemind.api.tool.ToolRegistry;
import com.codemind.impl.safety.PermissionGateImpl;
import com.codemind.impl.session.SessionManagerImpl;
import com.codemind.impl.skill.SkillRegistryImpl;
import com.codemind.impl.tool.ToolRegistryImpl;

/**
 * 应用依赖注入配置
 * 
 * 集中管理所有依赖的创建和绑定。
 * 
 * 设计原则：
 * - 依赖倒置原则（DIP）：通过接口创建依赖
 * - 单一职责原则（SRP）：只负责依赖创建，不承担业务逻辑
 * - 手动 DI：在 bootstrap/ 包中集中管理依赖创建顺序
 * 
 * 使用方式：
 * <pre>
 * AppBinder binder = new AppBinder();
 * ToolRegistry toolRegistry = binder.createToolRegistry(true);
 * SkillRegistry skillRegistry = binder.createSkillRegistry(toolRegistry);
 * </pre>
 */
public class AppBinder {
    
    /**
     * 创建权限网关
     * 
     * @param confirmDangerous 是否需要确认危险操作
     * @return PermissionGate 实例
     */
    public PermissionGate createPermissionGate(boolean confirmDangerous) {
        return new PermissionGateImpl(confirmDangerous);
    }
    
    /**
     * 创建工具注册中心
     * 
     * @param permissionGate 权限网关
     * @return 配置好的 ToolRegistry 实例
     */
    public ToolRegistry createToolRegistry(PermissionGate permissionGate) {
        ToolRegistryImpl registry = new ToolRegistryImpl(permissionGate);
        
        // 注册默认工具（注意：这些工具实例化可能有副作用，
        // 在实际应用中可能需要通过工厂模式创建）
        // 这里只是演示注册流程，实际工具创建可能需要更多配置
        return registry;
    }
    
    /**
     * 创建工具注册中心（使用默认权限网关）
     * 
     * @return 配置好的 ToolRegistry 实例
     */
    public ToolRegistry createToolRegistry() {
        return createToolRegistry(createPermissionGate(true));
    }
    
    /**
     * 创建技能注册中心
     * 
     * @param toolRegistry 工具注册中心
     * @return 配置好的 SkillRegistry 实例
     */
    public SkillRegistry createSkillRegistry(ToolRegistry toolRegistry) {
        return new SkillRegistryImpl(toolRegistry);
    }
    
    /**
     * 创建会话管理器
     * 
     * @return SessionManager 实例
     */
    public SessionManager createSessionManager() {
        return new SessionManagerImpl();
    }
    
    /**
     * 创建完整的应用依赖图
     * 
     * @return 包含所有核心依赖的配置对象
     */
    public AppDependencies createDependencies() {
        // 按依赖顺序创建
        PermissionGate permissionGate = createPermissionGate(true);
        ToolRegistry toolRegistry = createToolRegistry(permissionGate);
        SkillRegistry skillRegistry = createSkillRegistry(toolRegistry);
        SessionManager sessionManager = createSessionManager();
        
        return new AppDependencies(
            permissionGate,
            toolRegistry,
            skillRegistry,
            sessionManager
        );
    }
    
    /**
     * 应用依赖配置
     * 
     * 包含所有核心依赖的引用，便于一次性注入到需要的地方。
     */
    public static class AppDependencies {
        
        private final PermissionGate permissionGate;
        private final ToolRegistry toolRegistry;
        private final SkillRegistry skillRegistry;
        private final SessionManager sessionManager;
        
        public AppDependencies(PermissionGate permissionGate,
                               ToolRegistry toolRegistry,
                               SkillRegistry skillRegistry,
                               SessionManager sessionManager) {
            this.permissionGate = permissionGate;
            this.toolRegistry = toolRegistry;
            this.skillRegistry = skillRegistry;
            this.sessionManager = sessionManager;
        }
        
        public PermissionGate getPermissionGate() {
            return permissionGate;
        }
        
        public ToolRegistry getToolRegistry() {
            return toolRegistry;
        }
        
        public SkillRegistry getSkillRegistry() {
            return skillRegistry;
        }
        
        public SessionManager getSessionManager() {
            return sessionManager;
        }
    }
}