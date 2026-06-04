package com.codemind.impl.bootstrap;

import com.codemind.api.llm.LLMClient;
import com.codemind.api.safety.PermissionGate;
import com.codemind.api.session.SessionManager;
import com.codemind.api.skill.SkillExecutor;
import com.codemind.api.skill.SkillRegistry;
import com.codemind.api.tool.ToolRegistry;
import com.codemind.impl.safety.PermissionGateImpl;
import com.codemind.impl.session.SessionManagerImpl;
import com.codemind.impl.skill.*;
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
     * 注册所有 Skill Executor
     * 
     * 工作流程：
     * 1. 创建 SkillLoader 加载 Skill 定义
     * 2. 创建 SkillExecutor 实例
     * 3. 注册 Executor 到 SkillLoader
     * 
     * @param skillLoader Skill 加载器
     * @param skillsDir Skill 目录路径（包含 SKILL.md 文件）
     */
    public void registerSkillExecutors(SkillLoader skillLoader, String skillsDir) {
        // 1. 加载 Skill 定义
        List<SkillDefinition> definitions = loadSkills(skillLoader, skillsDir);
        
        // 2. 注册 Executor（匹配定义中的名称）
        // 注意：Executor 名称需与 SKILL.md 中的 name 字段一致
        skillLoader.registerExecutor("code_review", new CodeReviewSkill());
        skillLoader.registerExecutor("generate_docs", new DocGenSkill());
        skillLoader.registerExecutor("analyze_logs", new LogAnalysisSkill());
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
     * 创建 SkillLoader
     * 
     * @return SkillLoader 实例
     */
    public SkillLoader createSkillLoader() {
        return new SkillLoader();
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
     * 注册 Skill Executor
     * 
     * @param loader Skill 加载器
     * @param skillName Skill 名称
     * @param executor 执行器
     */
    public void registerSkillExecutor(SkillLoader loader, String skillName, SkillExecutor executor) {
        loader.registerExecutor(skillName, executor);
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