package com.codemind.agent.pattern.planexec;

import com.codemind.agent.SystemPromptBuilder;
import com.codemind.agent.engine.ExecutionState;
import com.codemind.agent.engine.TokenBudget;
import com.codemind.agent.pattern.planexec.compaction.PlanAwareCompactor;
import com.codemind.agent.pattern.planexec.handler.*;
import com.codemind.agent.spi.AgentPattern;
import com.codemind.agent.statemachine.StateHandler;
import com.codemind.context.ContextCompressionOrchestrator;
import com.codemind.frontend.output.spi.OutputFormatter;
import com.codemind.llm.LLMClient;
import com.codemind.tool.ToolRegistry;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.function.Function;

/**
 * Plan-and-Execute 模式的 AgentPattern 适配器。
 *
 * <p>将 {@code WorkflowOrchestrator} 中原有的硬编码 Handler 构建逻辑
 * 提取到此适配器中，通过 {@link #createHandlers} 返回 Plan 专用的 Handler 映射表。</p>
 *
 * <p>与 {@link com.codemind.agent.pattern.react.ReactAgentPattern} 并列，
 * 用户可按任务复杂度选择适配范式。</p>
 */
public class PlanAgentPattern implements AgentPattern {

    @Override
    public String name() {
        return "plan-exec";
    }

    @Override
    public Object initialState() {
        return PlanState.PLAN_GENERATE;
    }

    @Override
    public Map<Object, StateHandler> createHandlers(
            LLMClient llmClient,
            ToolRegistry toolRegistry,
            OutputFormatter outputFormatter,
            ContextCompressionOrchestrator compactionPipeline,
            TokenBudget tokenBudget,
            SystemPromptBuilder promptBuilder,
            Function<ExecutionState, String> compacter,
            Map<String, String> fileContentCache,
            int llmStreamingTimeoutSeconds) {

        Map<Object, StateHandler> map = new LinkedHashMap<>();

        // === 规划阶段 ===
        map.put(PlanState.PLAN_GENERATE, new PlanGenerateHandler(
            llmClient, toolRegistry, outputFormatter, tokenBudget,
            compactionPipeline, compacter, llmStreamingTimeoutSeconds));
        map.put(PlanState.PLAN_VALIDATE, new PlanValidateHandler(toolRegistry));

        // === 执行阶段 ===
        PlanAwareCompactor planCompactor = new PlanAwareCompactor();
        map.put(PlanState.STEP_EXECUTE, new StepExecuteHandler(
            toolRegistry, outputFormatter, fileContentCache, planCompactor, tokenBudget));
        map.put(PlanState.STEP_VERIFY, new StepVerifyHandler(llmClient, outputFormatter));

        // === 恢复阶段 ===
        map.put(PlanState.STEP_RETRY, new StepRetryHandler());
        map.put(PlanState.LOCAL_REPLAN, new ReplanHandler(
            llmClient, toolRegistry, false, outputFormatter));
        map.put(PlanState.GLOBAL_REPLAN, new ReplanHandler(
            llmClient, toolRegistry, true, outputFormatter));
        map.put(PlanState.SUB_AGENT_OFFLOAD, new SubAgentOffloadHandler(outputFormatter));
        map.put(PlanState.COMPACT_CONTEXT, new CompactContextHandler(compacter));
        // STEP_SKIPPED 和 PLAN_PARTIAL 的 Handler（之前缺失导致 NPE）
        map.put(PlanState.STEP_SKIPPED, new StepSkipHandler(outputFormatter));
        map.put(PlanState.PLAN_PARTIAL, new PlanPartialHandler(outputFormatter, fileContentCache));

        return map;
    }
}
