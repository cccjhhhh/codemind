package com.codemind.agent;

import com.codemind.llm.Message;
import com.codemind.session.SessionContext;
import com.codemind.agent.engine.WorkflowOrchestrator;
import com.codemind.agent.spi.AgentResult;
import com.codemind.agent.engine.TokenBudget;
import com.codemind.skill.SkillRouteDto;
import com.codemind.agent.SystemPromptBuilder;
import com.codemind.safety.SafetyChecker;
import com.codemind.session.CompactionPipeline;
import com.codemind.skill.routing.SkillRouter;
import com.codemind.frontend.output.spi.OutputFormatter;
import com.codemind.agent.pattern.react.ReactAgentPattern;
import com.codemind.llm.LLMClient;
import com.codemind.safety.PermissionGate;
import com.codemind.tool.ToolRegistry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.function.Consumer;

/**
 * Agent 主循环 — 瘦路由器。
 *
 * 【规则 02-agent-loop-scope】
 * 单文件 ≤ 200 行，不直接调用 tool/LLM/context 操作。
 * 执行逻辑全部委托给 {@link WorkflowOrchestrator}。
 */
public class AgentLoop {

    private static final Logger log = LoggerFactory.getLogger(AgentLoop.class);

    private final PermissionGate permissionGate;
    private final SkillRouter skillRouter;
    private final WorkflowOrchestrator orchestrator;

    public AgentLoop(LLMClient llmClient, ToolRegistry toolRegistry,
                     PermissionGate permissionGate, OutputFormatter outputFormatter,
                     int maxIterations, int maxExecutionTimeSeconds,
                     int llmStreamingTimeoutSeconds,
                     SkillRouter skillRouter, SystemPromptBuilder promptBuilder,
                     CompactionPipeline compactionPipeline, TokenBudget tokenBudget) {
        this.permissionGate = permissionGate;
        this.skillRouter = skillRouter;
        this.orchestrator = new WorkflowOrchestrator(
            llmClient, toolRegistry, outputFormatter,
            maxIterations, maxExecutionTimeSeconds, llmStreamingTimeoutSeconds,
            promptBuilder, compactionPipeline, tokenBudget,
            new ReactAgentPattern());
    }

    // ==================== 公共入口 ====================

    public AgentResult runStream(String input, SessionContext context,
                                 Consumer<String> outputHandler) {
        try {
            SafetyChecker safetyChecker = new SafetyChecker();

            AgentResult safetyResult = validateInput(input, safetyChecker);
            if (safetyResult != null) return safetyResult;

            if (context.hasActiveSkill()) context.clearActiveSkill();
            tryActivateSkill(input, context);

            context.addMessage(Message.user(input));
            return orchestrator.execute(context, outputHandler, System.currentTimeMillis(), safetyChecker);

        } catch (Exception e) {
            log.error("Agent 执行异常", e);
            return AgentResult.failure("Agent 执行失败: " + e.getMessage());
        }
    }

    public AgentResult run(String input, SessionContext context) {
        return runStream(input, context, token -> {});
    }

    // ==================== 输入验证 ====================

    private AgentResult validateInput(String input, SafetyChecker safetyChecker) {
        if (!safetyChecker.isInputSafe(input)) {
            log.warn("输入包含不安全内容，已拒绝");
            return AgentResult.failure("输入包含不安全内容，请检查您的输入");
        }
        if (safetyChecker.detectPromptInjection(input)) {
            log.warn("检测到 Prompt 注入尝试，已拒绝");
            return AgentResult.failure("检测到 Prompt 注入尝试，请使用正常方式交流");
        }
        return null;
    }

    private boolean tryActivateSkill(String input, SessionContext context) {
        if (skillRouter == null) return false;
        SkillRouteDto route = skillRouter.route(input);
        if (route == null || !route.shouldExecute()) return false;
        context.setActiveSkill(route.skill());
        return true;
    }

    // ==================== 子 Agent ====================

    public AgentLoop createSubAgent() {
        return new AgentLoop(
            orchestrator.getLlmClient(),
            orchestrator.getToolRegistry(),
            permissionGate,
            orchestrator.getOutputFormatter(),
            15, 60, 30, null, null, null, null
        );
    }
}
