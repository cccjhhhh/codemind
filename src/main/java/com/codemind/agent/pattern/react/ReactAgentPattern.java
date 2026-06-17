package com.codemind.agent.pattern.react;

import com.codemind.agent.SystemPromptBuilder;
import com.codemind.agent.engine.ExecutionState;
import com.codemind.agent.engine.TokenBudget;
import com.codemind.agent.recovery.*;
import com.codemind.agent.spi.AgentPattern;
import com.codemind.agent.statemachine.ContinueReason;
import com.codemind.agent.statemachine.StateHandler;
import com.codemind.agent.statemachine.pattern.ReactState;
import com.codemind.context.ContextCompressionOrchestrator;
import com.codemind.frontend.output.spi.OutputFormatter;
import com.codemind.llm.LLMClient;
import com.codemind.tool.ToolRegistry;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.function.Function;

/**
 * ReAct 模式的 AgentPattern 适配器。
 *
 * <p>将 {@code WorkflowOrchestrator} 中原有的硬编码 Handler 构建逻辑
 * 提取到此适配器中，通过 {@link #createHandlers} 返回 ReAct 专用的 Handler 映射表。</p>
 */
public class ReactAgentPattern implements AgentPattern {

    @Override
    public String name() {
        return "react";
    }

    @Override
    public Object initialState() {
        return ReactState.THINK;
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

        StopHook stopHook = new StopHook();
        ToolRetryStrategy toolRetryStrategy = new ToolRetryStrategy();

        // ReAct 核心处理器
        map.put(ReactState.THINK, new ThinkHandler(
            llmClient, toolRegistry, outputFormatter, compactionPipeline,
            tokenBudget, stopHook, promptBuilder, compacter, llmStreamingTimeoutSeconds));
        map.put(ReactState.ACT, new ActHandler(
            outputFormatter, toolRegistry, toolRetryStrategy, fileContentCache));

        // 恢复处理器 — 复用 recovery 包中的通用 Handler
        map.put(ContinueReason.RECOVERY_COMPACT, new CompactHandler(compacter));
        map.put(ContinueReason.LOOP_DETECTED, new LoopBreakHandler(outputFormatter, compacter));
        map.put(ContinueReason.TOKEN_BUDGET_CONTINUE, new BudgetHandler(outputFormatter));
        map.put(ContinueReason.RECOVERY_ESCALATE, new EscalateHandler());
        map.put(ContinueReason.CONTINUATION, new ContinuationHandler());
        map.put(ContinueReason.RETRY_BACKOFF, new RetryHandler());
        map.put(ContinueReason.RECOVERY_FAILOVER, new FailoverHandler(outputFormatter));

        return map;
    }
}
