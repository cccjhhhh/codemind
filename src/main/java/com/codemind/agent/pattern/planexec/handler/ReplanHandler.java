package com.codemind.agent.pattern.planexec.handler;

import com.codemind.agent.engine.ExecutionState;
import com.codemind.agent.pattern.planexec.*;
import com.codemind.agent.statemachine.HandlerResult;
import com.codemind.agent.statemachine.StateHandler;
import com.codemind.frontend.output.spi.OutputFormatter;
import com.codemind.llm.*;
import com.codemind.session.SessionContext;
import com.codemind.tool.ToolRegistry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;
import java.util.stream.Collectors;

/**
 * ReplanHandler — 局部/全局重规划。
 *
 * <p>重规划策略：</p>
 * <ul>
 *   <li>{@link #LOCAL_REPLAN} — 从失败点开始重新生成剩余步骤</li>
 *   <li>{@link #GLOBAL_REPLAN} — 全量重新生成计划</li>
 * </ul>
 *
 * <p>重规划时注入已失败步骤的信息，帮助 LLM 避免重复错误。</p>
 *
 * <p>重规划失败 → PLAN_PARTIAL（返回已完成步骤的结果）。</p>
 */
public class ReplanHandler implements StateHandler {

    private static final Logger log = LoggerFactory.getLogger(ReplanHandler.class);

    private final LLMClient llmClient;
    private final ToolRegistry toolRegistry;
    private final boolean global;
    private final OutputFormatter outputFormatter;
    private static final int MAX_REPLAN_ATTEMPTS = 2;

    public ReplanHandler(LLMClient llmClient, ToolRegistry toolRegistry, boolean global,
                         OutputFormatter outputFormatter) {
        this.llmClient = llmClient;
        this.toolRegistry = toolRegistry;
        this.global = global;
        this.outputFormatter = outputFormatter;
    }

    @Override
    public HandlerResult handle(ExecutionState state) {
        SessionContext ctx = state.sessionContext;
        PlanExecutionState pes = getPlanState(ctx);
        if (pes == null || pes.getPlan() == null) {
            return HandlerResult.withCount(PlanState.PLAN_PARTIAL);
        }

        ExecutionPlan originalPlan = pes.getPlan();
        String goal = originalPlan.goal();
        int failedCount = pes.getFailedCount();
        int totalSteps = originalPlan.steps().size();

        // 构建失败上下文
        String failureContext = buildFailureContext(pes);

        // 确定重规划范围
        List<PlanStep> remainingSteps;
        if (global) {
            remainingSteps = originalPlan.steps();
            log.info("全局重规划：失败 {}/{} 步骤", failedCount, totalSteps);
            outputFormatter.formatReplanTriggered("全局重规划（失败 " + failedCount + "/" + totalSteps + "）");
        } else {
            remainingSteps = originalPlan.steps().subList(
                Math.min(pes.getCurrentStepIndex(), originalPlan.steps().size()),
                originalPlan.steps().size());
            log.info("局部重规划：剩余 {} 步骤，已失败 {} 步", remainingSteps.size(), failedCount);
            outputFormatter.formatReplanTriggered("局部重规划（剩余 " + remainingSteps.size() + " 步骤）");
        }

        // 重试重规划
        ExecutionPlan newPlan = null;
        for (int attempt = 1; attempt <= MAX_REPLAN_ATTEMPTS; attempt++) {
            newPlan = callReplanLLM(goal, remainingSteps, failureContext, originalPlan.context());
            if (newPlan != null && !newPlan.steps().isEmpty()) break;
        }

        if (newPlan == null || newPlan.steps().isEmpty()) {
            log.error("重规划 {} 次均失败，返回 PARTIAL_SUCCESS", MAX_REPLAN_ATTEMPTS);
            return HandlerResult.withoutCount(PlanState.PLAN_PARTIAL);
        }

        // 验证新计划
        pes.updatePlan(newPlan);
        return HandlerResult.withCount(PlanState.PLAN_VALIDATE);
    }

    // ==================== 重规划 LLM 调用 ====================

    private ExecutionPlan callReplanLLM(String goal, List<PlanStep> remainingSteps,
                                         String failureContext, PlanContext context) {
        String prompt = buildReplanPrompt(goal, remainingSteps, failureContext, context);
        List<ToolDefinition> tools = toolRegistry.getDefinitionsForSkill(null);

        AtomicReference<String> fullText = new AtomicReference<>("");
        CountDownLatch latch = new CountDownLatch(1);

        try {
            llmClient.chatStreamWithTools(
                List.of(Message.user(prompt)), tools,
                new LLMClient.StreamHandler() {
                    @Override
                    public void onEvent(StreamEvent event) {
                        if (event.isTextDelta()) {
                            fullText.updateAndGet(v -> v + event.getTextDelta());
                        } else if (event.isMessageComplete() || event.isError()) {
                            latch.countDown();
                        }
                    }
                    @Override
                    public void onError(Exception e) {
                        latch.countDown();
                    }
                }, 30);

            if (!latch.await(30, TimeUnit.SECONDS)) {
                log.warn("重规划 LLM 调用超时");
                return null;
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return null;
        }

        String response = fullText.get();
        if (response.isBlank()) return null;

        return SimplePlanParser.parse(response);
    }

    // ==================== Prompt 构建 ====================

    private String buildReplanPrompt(String goal, List<PlanStep> remainingSteps,
                                      String failureContext, PlanContext context) {
        return String.format("""
            执行计划需要调整。请重新生成剩余步骤。

            【原始目标】
            %s

            【已失败步骤】
            %s

            【剩余步骤（需要重新规划）】
            %s

            【全局约束】
            %s

            【要求】
            1. 避免之前失败的模式
            2. 每步最多 3 个工具调用
            3. 只返回 JSON，格式与原始计划相同
            """,
            goal,
            failureContext,
            formatSteps(remainingSteps),
            context.globalConstraints().isEmpty() ? "无" : String.join(", ", context.globalConstraints())
        );
    }

    private String buildFailureContext(PlanExecutionState pes) {
        return pes.getFailedSteps().stream()
            .map(r -> String.format("[%s] %s: %s", r.stepId(), r.failureMode(), r.errorMessage()))
            .reduce((a, b) -> a + "\n" + b)
            .orElse("无");
    }

    private String formatSteps(List<PlanStep> steps) {
        if (steps.isEmpty()) return "无";
        return steps.stream()
            .map(s -> String.format("%s: %s (tools: %d)", s.id(), s.description(), s.tools().size()))
            .reduce((a, b) -> a + "\n" + b)
            .orElse("");
    }

    // ==================== 辅助方法 ====================

    private PlanExecutionState getPlanState(SessionContext ctx) {
        Object v = ctx.getVariable("_planExecutionState");
        return v instanceof PlanExecutionState ? (PlanExecutionState) v : null;
    }
}
