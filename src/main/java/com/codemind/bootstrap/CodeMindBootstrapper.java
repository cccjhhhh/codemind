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
import com.codemind.impl.llm.ModelFactory;
import com.codemind.impl.llm.ModelManager;
import com.codemind.impl.safety.PermissionGateImpl;
import com.codemind.impl.session.SessionManagerImpl;
import com.codemind.impl.skill.SkillDefinition;
import com.codemind.impl.skill.SkillLoader;
import com.codemind.impl.skill.routing.SkillRouter;
import com.codemind.impl.tool.*;

import java.nio.file.Path;
import java.util.List;

/**
 * One-class startup assembler.
 * Creates all core components and auto-discovers skills from classpath.
 */
public class CodeMindBootstrapper {

    public BootstrapResult bootstrap(Path projectDir) {
        // 1. Infrastructure
        DefaultOutputFormatter outputFormatter = new DefaultOutputFormatter();
        CLIPermissionPrompter prompter = new CLIPermissionPrompter(outputFormatter);
        PermissionGateImpl permissionGate = new PermissionGateImpl(prompter);

        // 2. Tools
        ToolRegistryImpl toolRegistry = new ToolRegistryImpl(permissionGate);
        toolRegistry.register(new ReadTool());
        toolRegistry.register(new WriteTool());
        toolRegistry.register(new EditTool());
        toolRegistry.register(new GrepTool());
        toolRegistry.register(new GlobTool());
        toolRegistry.register(new BashTool());
        toolRegistry.register(new WebFetchTool());

        // 3. Skills — auto-discovered from classpath, zero registration code
        SkillLoader skillLoader = new SkillLoader();
        List<SkillDefinition> skills = skillLoader.loadAllFromClasspath(null);

        // 4. LLM
        LLMClient llmClient = ModelFactory.create(new ModelManager().getCurrentModel());

        // 5. Skill router
        SkillRouter skillRouter = new SkillRouter(llmClient, skills);

        // 6. System prompt builder
        SystemPromptBuilder promptBuilder = new SystemPromptBuilder(toolRegistry, skills);

        // 7. Session
        SessionManagerImpl sessionManager = new SessionManagerImpl();
        SessionContext session = sessionManager.createSession();
        session.setWorkingDirectory(projectDir);
        session.setSystemMessage(promptBuilder.build(session));

        // 8. Agent loop
        AgentLoop agentLoop = new AgentLoop(
            llmClient, toolRegistry, permissionGate, outputFormatter,
            50, 300, skillRouter, promptBuilder
        );

        return new BootstrapResult(agentLoop, session, sessionManager, toolRegistry, permissionGate);
    }

    public record BootstrapResult(
        AgentLoop agentLoop,
        SessionContext session,
        SessionManager sessionManager,
        ToolRegistry toolRegistry,
        PermissionGate permissionGate
    ) {}
}
