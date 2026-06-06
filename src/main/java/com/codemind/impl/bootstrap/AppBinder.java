package com.codemind.impl.bootstrap;

import com.codemind.api.llm.LLMClient;
import com.codemind.api.safety.PermissionGate;
import com.codemind.api.safety.PermissionPrompter;
import com.codemind.api.session.SessionManager;
import com.codemind.api.tool.ToolRegistry;
import com.codemind.impl.cli.CLIPermissionPrompter;
import com.codemind.impl.safety.PermissionGateImpl;
import com.codemind.impl.session.SessionManagerImpl;
import com.codemind.impl.skill.SkillDefinition;
import com.codemind.impl.skill.SkillLoader;
import com.codemind.impl.skill.routing.SkillRouter;
import com.codemind.impl.tool.ToolRegistryImpl;

import java.io.IOException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;

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
 * </pre>
 */
public class AppBinder {
    
    /**
     * 创建权限网关（推荐方式）
     *
     * @param permissionPrompter 权限询问器（用于用户交互）
     * @return PermissionGate 实例
     */
    public PermissionGate createPermissionGate(PermissionPrompter permissionPrompter) {
        return new PermissionGateImpl(permissionPrompter);
    }

    /**
     * 创建权限网关（使用 CLI 权限询问器）
     *
     * @return PermissionGate 实例
     */
    public PermissionGate createPermissionGate() {
        return new PermissionGateImpl(new CLIPermissionPrompter());
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
        return createToolRegistry(createPermissionGate());
    }
    
    /**
     * 创建 SkillLoader
     *
     * @return SkillLoader 实例
     */
    public SkillLoader createSkillLoader() {
        return new SkillLoader();
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
     * 从 classpath 加载所有 Skill（通用方案）
     * 
     * 自动扫描 classpath 中的 skills/ 目录，适用于任何项目
     * 
     * @param skillLoader 加载器
     * @param classLoader 类加载器，null 则使用当前线程的上下文类加载器
     * @return 加载的 Skill 列表
     */
    public List<SkillDefinition> loadSkillsFromClasspath(SkillLoader skillLoader, ClassLoader classLoader) {
        return skillLoader.loadAllFromClasspath(classLoader);
    }
    
    /**
     * 从指定目录加载所有 Skill
     * 
     * @param skillLoader 加载器
     * @param skillsDir Skill 目录路径
     * @return 加载的 Skill 列表
     */
    public List<SkillDefinition> loadSkills(SkillLoader skillLoader, String skillsDir) {
        try {
            Path skillsPath = Paths.get(skillsDir);
            return skillLoader.loadAll(skillsPath);
        } catch (IOException e) {
            throw new RuntimeException("Failed to load skills from: " + skillsDir, e);
        }
    }
    
    /**
     * 创建 SkillRouter（支持语义路由）
     * 
     * @param llmClient LLM 客户端（用于语义路由）
     * @param skills Skill 定义列表
     * @return SkillRouter 实例
     */
    public SkillRouter createSkillRouter(LLMClient llmClient, List<SkillDefinition> skills) {
        return new SkillRouter(llmClient, skills);
    }
    
    /**
     * 创建完整的应用依赖图
     *
     * @return 包含所有核心依赖的配置对象
     */
    public AppDependencies createDependencies() {
        // 按依赖顺序创建
        PermissionGate permissionGate = createPermissionGate();
        ToolRegistry toolRegistry = createToolRegistry(permissionGate);
        SessionManager sessionManager = createSessionManager();

        return new AppDependencies(
            permissionGate,
            toolRegistry,
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
        private final SessionManager sessionManager;

        public AppDependencies(PermissionGate permissionGate,
                               ToolRegistry toolRegistry,
                               SessionManager sessionManager) {
            this.permissionGate = permissionGate;
            this.toolRegistry = toolRegistry;
            this.sessionManager = sessionManager;
        }

        public PermissionGate getPermissionGate() {
            return permissionGate;
        }

        public ToolRegistry getToolRegistry() {
            return toolRegistry;
        }

        public SessionManager getSessionManager() {
            return sessionManager;
        }
    }
}
