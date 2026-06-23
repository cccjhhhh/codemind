package com.codemind.bootstrap;

import com.codemind.agent.AgentLoop;
import com.codemind.agent.SystemPromptBuilder;
import com.codemind.agent.engine.TokenBudget;
import com.codemind.config.Settings;
import com.codemind.config.SettingsLoader;
import com.codemind.context.ContextCompressionOrchestrator;
import com.codemind.frontend.cli.CLIPermissionPrompter;
import com.codemind.frontend.cli.DefaultOutputFormatter;
import com.codemind.llm.LLMClient;
import com.codemind.llm.ModelFactory;
import com.codemind.llm.ModelManager;
import com.codemind.mcp.*;
import com.codemind.safety.PermissionGate;
import com.codemind.safety.PermissionGateImpl;
import com.codemind.safety.PermissionGateImpl.PermissionRule;
import com.codemind.safety.PermissionLevel;
import com.codemind.session.SessionContext;
import com.codemind.session.SessionManager;
import com.codemind.session.SessionManagerImpl;
import com.codemind.session.SlidingWindowContextManager;
import com.codemind.skill.ClasspathSkillProvider;
import com.codemind.skill.DirectorySkillProvider;
import com.codemind.skill.SkillDefinition;
import com.codemind.skill.SkillRegistry;
import com.codemind.skill.routing.ConfidenceSkillRouter;
import com.codemind.skill.routing.SkillRouter;
import com.codemind.tool.ToolRegistry;
import com.codemind.tool.ToolRegistryImpl;
import com.codemind.tool.hook.MetricsHook;
import com.codemind.tool.hook.PermissionPreHook;
import com.codemind.tool.hook.SafetyPreHook;
import com.codemind.tool.hook.TruncationHook;
import com.codemind.tool.impl.*;
import com.codemind.tool.spi.Tool;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public class CodeMindBootstrapper {

    // 与 Settings.java 和 CLI.java 保持一致的默认值
    private static final int DEFAULT_LLM_STREAMING_TIMEOUT_SECONDS = 300;

    public BootstrapResult bootstrap(Path projectDir) {
        return bootstrap(projectDir, 150, 300, null, DEFAULT_LLM_STREAMING_TIMEOUT_SECONDS);
    }

    public BootstrapResult bootstrap(Path projectDir, int maxIterations, int timeoutSeconds, Path configPath) {
        return bootstrap(projectDir, maxIterations, timeoutSeconds, configPath, DEFAULT_LLM_STREAMING_TIMEOUT_SECONDS);
    }

    public BootstrapResult bootstrap(Path projectDir, int maxIterations, int timeoutSeconds, Path configPath, int llmStreamingTimeoutSeconds) {
        // 1. 基础设施
        DefaultOutputFormatter outputFormatter = new DefaultOutputFormatter();
        CLIPermissionPrompter prompter = new CLIPermissionPrompter(outputFormatter);
        PermissionGateImpl permissionGate = new PermissionGateImpl(prompter);

        // 2. 工具
        ToolRegistryImpl toolRegistry = new ToolRegistryImpl();
        toolRegistry.register(new ReadTool());
        toolRegistry.register(new WriteTool());
        toolRegistry.register(new EditTool());
        toolRegistry.register(new GrepTool());
        toolRegistry.register(new GlobTool());
        toolRegistry.register(new BashTool());
        toolRegistry.register(new WebFetchTool());
        toolRegistry.register(new TodoTool());

        // 3. 配置加载（模型配置 + 技能目录 + 权限规则 + 上下文配置）
        SettingsLoader.ensureGlobalConfig();
        var settings = (configPath != null)
            ? SettingsLoader.loadChain(projectDir, configPath)
            : SettingsLoader.loadChain(projectDir);

        // 从 settings.json 加载权限规则
        if (!settings.getPermissions().getRules().isEmpty()) {
            var rules = settings.getPermissions().getRules().stream()
                .map(r -> new PermissionRule(r.getTool(), PermissionLevel.valueOf(r.getLevel())))
                .toList();
            permissionGate.setRules(rules);
        }
        if (!settings.getPermissions().getDeny().isEmpty()) {
            permissionGate.setDenyPatterns(settings.getPermissions().getDeny());
        }

        // 注册 Hook 链（顺序决定执行顺序）
        toolRegistry.registerHook(new SafetyPreHook());
        toolRegistry.registerHook(new PermissionPreHook(permissionGate));
        toolRegistry.registerHook(new MetricsHook());

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

        // 5. 模型管理器（从 settings.json 读取模型配置）
        ModelManager modelManager = new ModelManager(settings);

        // 6. 大语言模型
        LLMClient llmClient = ModelFactory.create(modelManager.getCurrentModel());

        // 7. 技能路由器（置信度路由 + 关键词兜底）
        SkillRouter skillRouter = new ConfidenceSkillRouter(skills);

        // 8. 系统提示构建器
        SystemPromptBuilder promptBuilder = new SystemPromptBuilder(toolRegistry, skillRegistry);

        // 9. 会话 — 使用实际模型的上下文窗口
        SlidingWindowContextManager contextManager = SlidingWindowContextManager.forModel(
            modelManager.getCurrentModelId()
        );
        contextManager.setTargetRatio(settings.getContext().getWindow().getTargetRatio());
        SessionManagerImpl sessionManager = new SessionManagerImpl(contextManager);
        SessionContext session = sessionManager.createSession();
        session.setWorkingDirectory(projectDir);

        // TruncationHook — 只做预览不做落盘（落盘由 L3 统一处理）
        var truncationCfg = settings.getContext().getTruncation();
        toolRegistry.registerHook(new TruncationHook(
            truncationCfg.getSpillThresholdChars()
        ));
        session.setSystemMessage(promptBuilder.build(session));

        // 10. Agent 参数覆盖逻辑：settings 为基准，CLI 参数覆盖
        int effectiveMaxIterations = settings.getAgent().getMaxIterations();
        int effectiveTimeout = settings.getAgent().getTimeoutSeconds();
        int effectiveLlmStreamingTimeout = settings.getAgent().getLlmStreamingTimeoutSeconds();
        if (maxIterations != 150) effectiveMaxIterations = maxIterations;
        if (timeoutSeconds != 300) effectiveTimeout = timeoutSeconds;
        if (llmStreamingTimeoutSeconds != DEFAULT_LLM_STREAMING_TIMEOUT_SECONDS) effectiveLlmStreamingTimeout = llmStreamingTimeoutSeconds;

        // 11. 创建 CompactionPipeline（依赖 effectiveTimeout 用于 L4 超时检查）
        Settings.CompactionConfig compCfg = settings.getContext().getCompaction();
        Path spillDirResolved = Path.of(truncationCfg.getSpillDir());
        ContextCompressionOrchestrator compactionPipeline = ContextCompressionOrchestrator.createDefault(
            compCfg.getL1MaxRounds(),
            compCfg.getL2MaxCompactions(),
            compCfg.getL2KeepRecentRounds(),
            compCfg.getBudgetMaxBytes(),
            spillDirResolved,
            truncationCfg.getSpillThresholdChars(),
            session.getSessionId(),
            compCfg.isSaveTranscripts(),
            llmClient,
            effectiveTimeout,
            compCfg.getCompressOnRounds()
        );

        // 12. 创建 TokenBudget
        TokenBudget tokenBudget = new TokenBudget(
            contextManager.getTokenCountService(),
            contextManager.getReservedResponseTokens(),
            settings.getContext().getWindow().getTargetRatio()
        );

        // MCP 初始化 — 使用 McpToolRegistry 管理 MCP 工具生命周期
        // 同时注册到主 ToolRegistry 以获得完整 Hook 链
        McpToolRegistry mcpToolRegistry = new McpToolRegistry();
        try {
            Path mcpConfigPath = Path.of(System.getProperty("user.home"), ".codemind", "mcp.json");
            McpConfigLoader configLoader = new McpConfigLoader();
            Map<String, McpServerConfig> mcpServers = configLoader.load(mcpConfigPath);

            McpClientFactory clientFactory = new McpClientFactoryImpl();
            McpToolAdapter toolAdapter = new McpToolAdapterImpl();

            for (Map.Entry<String, McpServerConfig> entry : mcpServers.entrySet()) {
                String serverName = entry.getKey();
                McpServerConfig config = entry.getValue();

                if (config.isEnabled()) {
                    try {
                        McpClient client = clientFactory.createClient(config);
                        client.connect(config);

                        List<McpToolDefinition> toolDefs = client.listTools();
                        List<Tool> adaptedTools = new ArrayList<>();
                        for (McpToolDefinition toolDef : toolDefs) {
                            Tool adapted = toolAdapter.adapt(toolDef, client);
                            adaptedTools.add(adapted);
                        }
                        // 1. 注册到 McpToolRegistry（维护 server→tools 映射，支持按服务器管理）
                        mcpToolRegistry.registerServerTools(serverName, adaptedTools);
                        // 2. 注册到主 ToolRegistry（获得完整 Hook 链）
                        for (Tool adapted : adaptedTools) {
                            toolRegistry.register(adapted);
                        }
                        System.out.println("Connected to MCP server: " + serverName);
                    } catch (Exception e) {
                        System.out.println("Failed to connect to MCP server " + serverName + ": " + e.getMessage());
                    }
                }
            }
        } catch (Exception e) {
            System.out.println("Failed to load MCP config: " + e.getMessage());
        }

        // 13. Agent 循环（MCP 工具已注册到主 ToolRegistry）
        AgentLoop agentLoop = new AgentLoop(
            llmClient, toolRegistry, permissionGate, outputFormatter,
            effectiveMaxIterations, effectiveTimeout, effectiveLlmStreamingTimeout,
            skillRouter, promptBuilder,
            compactionPipeline, tokenBudget
        );

        // 注册依赖 AgentLoop 的工具
        toolRegistry.register(new TaskTool(agentLoop, projectDir, settings));
        session.setSystemMessage(promptBuilder.build(session));

        return new BootstrapResult(agentLoop, session, sessionManager, toolRegistry,
            mcpToolRegistry, permissionGate, skillRouter, promptBuilder, modelManager,
            settings, effectiveMaxIterations, effectiveTimeout, effectiveLlmStreamingTimeout);
    }

    public record BootstrapResult(
        AgentLoop agentLoop,
        SessionContext session,
        SessionManager sessionManager,
        ToolRegistry toolRegistry,
        McpToolRegistry mcpToolRegistry,
        PermissionGate permissionGate,
        SkillRouter skillRouter,
        SystemPromptBuilder promptBuilder,
        ModelManager modelManager,
        Settings settings,
        int effectiveMaxIterations,
        int effectiveTimeoutSeconds,
        int effectiveLlmStreamingTimeoutSeconds
    ) {}
}
