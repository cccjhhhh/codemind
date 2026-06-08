package com.codemind.bootstrap;

import com.codemind.api.llm.LLMClient;
import com.codemind.api.safety.PermissionGate;
import com.codemind.api.session.SessionContext;
import com.codemind.api.session.SessionManager;
import com.codemind.api.tool.ToolRegistry;
import com.codemind.core.AgentLoop;
import com.codemind.impl.cli.CLIPermissionPrompter;
import com.codemind.impl.cli.DefaultOutputFormatter;
import com.codemind.impl.cli.SystemPromptBuilder;
import com.codemind.impl.config.SettingsLoader;
import com.codemind.impl.llm.ModelFactory;
import com.codemind.impl.llm.ModelManager;
import com.codemind.impl.safety.PermissionGateImpl;
import com.codemind.impl.session.SessionManagerImpl;
import com.codemind.impl.session.SlidingWindowContextManager;
import com.codemind.impl.skill.ClasspathSkillProvider;
import com.codemind.impl.skill.DirectorySkillProvider;
import com.codemind.impl.skill.SkillDefinition;
import com.codemind.impl.skill.SkillRegistry;
import com.codemind.impl.skill.routing.SkillRouter;
import com.codemind.impl.tool.*;

import java.nio.file.Path;
import java.util.List;

/**
 * 单类启动组装器。
 * 创建所有核心组件并从类路径自动发现技能。
 */
public class CodeMindBootstrapper {

    public BootstrapResult bootstrap(Path projectDir) {
        // 创建共享的 ModelManager 实例（避免重复创建）
        ModelManager modelManager = new ModelManager();

        // 1. 基础设施
        DefaultOutputFormatter outputFormatter = new DefaultOutputFormatter();
        CLIPermissionPrompter prompter = new CLIPermissionPrompter(outputFormatter);
        PermissionGateImpl permissionGate = new PermissionGateImpl(prompter);

        // 2. 工具
        ToolRegistryImpl toolRegistry = new ToolRegistryImpl(permissionGate);
        toolRegistry.register(new ReadTool());
        toolRegistry.register(new WriteTool());
        toolRegistry.register(new EditTool());
        toolRegistry.register(new GrepTool());
        toolRegistry.register(new GlobTool());
        toolRegistry.register(new BashTool());
        toolRegistry.register(new WebFetchTool());

        // 3. 配置加载
        var settings = SettingsLoader.loadChain(projectDir);

        // 4. 技能 — SkillRegistry 多来源发现
        SkillRegistry skillRegistry = new SkillRegistry();
        skillRegistry.addProvider(new ClasspathSkillProvider(null));
        if (!settings.getSkillDirectories().isEmpty()) {
            List<Path> dirs = settings.getSkillDirectories().stream()
                .map(d -> d.startsWith("~") ? Path.of(System.getProperty("user.home"), d.substring(1)) : Path.of(d))
                .toList();
            skillRegistry.addProvider(new DirectorySkillProvider(dirs));
        }
        skillRegistry.refresh();
        List<SkillDefinition> skills = skillRegistry.listAll().stream()
            .map(e -> new SkillDefinition(e.metadata()))
            .toList();

        // 注册需要 skillRegistry 的工具
        toolRegistry.register(new LoadSkillTool(skillRegistry));

        // 5. 大语言模型
        LLMClient llmClient = ModelFactory.create(modelManager.getCurrentModel());

        // 6. 技能路由器
        SkillRouter skillRouter = new SkillRouter(skills);

        // 7. 系统提示构建器
        SystemPromptBuilder promptBuilder = new SystemPromptBuilder(toolRegistry, skillRegistry);

        // 8. 会话 — 使用实际模型的上下文窗口
        SlidingWindowContextManager contextManager = SlidingWindowContextManager.forModel(
            modelManager.getCurrentModelId()
        );
        SessionManagerImpl sessionManager = new SessionManagerImpl(contextManager);
        SessionContext session = sessionManager.createSession();
        session.setWorkingDirectory(projectDir);
        session.setSystemMessage(promptBuilder.build(session));

        // 8. Agent 循环
        AgentLoop agentLoop = new AgentLoop(
            llmClient, toolRegistry, permissionGate, outputFormatter,
            50, 300, skillRouter, promptBuilder
        );

        return new BootstrapResult(agentLoop, session, sessionManager, toolRegistry, permissionGate, skillRouter, promptBuilder);
    }

    public record BootstrapResult(
        AgentLoop agentLoop,
        SessionContext session,
        SessionManager sessionManager,
        ToolRegistry toolRegistry,
        PermissionGate permissionGate,
        SkillRouter skillRouter,
        SystemPromptBuilder promptBuilder
    ) {}
}
