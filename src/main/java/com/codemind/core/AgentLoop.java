package com.codemind.core;

import com.codemind.api.llm.Message;
import com.codemind.api.session.SessionContext;
import com.codemind.core.service.execution.WorkflowOrchestrator;
import com.codemind.dto.skill.SkillRouteDto;
import com.codemind.impl.cli.SystemPromptBuilder;
import com.codemind.impl.safety.SafetyChecker;
import com.codemind.impl.session.CompactionPipeline;
import com.codemind.impl.skill.routing.SkillRouter;
import com.codemind.api.cli.OutputFormatter;
import com.codemind.api.llm.LLMClient;
import com.codemind.api.safety.PermissionGate;
import com.codemind.api.tool.ToolRegistry;
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
                     SkillRouter skillRouter, SystemPromptBuilder promptBuilder,
                     CompactionPipeline compactionPipeline, TokenBudget tokenBudget) {
        this.permissionGate = permissionGate;
        this.skillRouter = skillRouter;
        this.orchestrator = new WorkflowOrchestrator(
            llmClient, toolRegistry, outputFormatter,
            maxIterations, maxExecutionTimeSeconds,
            promptBuilder, compactionPipeline, tokenBudget);
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
            15, 60, null, null, null, null
        );
    }
}
