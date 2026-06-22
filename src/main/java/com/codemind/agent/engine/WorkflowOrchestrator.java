package com.codemind.agent.engine;

import com.codemind.agent.SystemPromptBuilder;
import com.codemind.agent.spi.AgentPattern;
import com.codemind.agent.spi.AgentResult;
import com.codemind.agent.statemachine.HandlerResult;
import com.codemind.agent.statemachine.StateHandler;
import com.codemind.agent.statemachine.TerminalState;
import com.codemind.agent.statemachine.pattern.ReactState;
import com.codemind.context.ContextCompressionOrchestrator;
import com.codemind.frontend.output.spi.OutputFormatter;
import com.codemind.llm.LLMClient;
import com.codemind.llm.Message;
import com.codemind.safety.SafetyChecker;
import com.codemind.session.SessionContext;
import com.codemind.tool.ToolRegistry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

/**
 * 执行编排器 — AgentLoop 状态机主循环。
 *
 * 职责：
 * 1. 持有全部 Handler 实例和 Handler 映射表
 * 2. 驱动 execute() 主循环：超时检查 → 熔断 → 分发 → 计数
 * 3. 持有跨请求的文件内容缓存 (LRU)
 * 4. 处理 COMPLETE/ERROR/MAX_ITERATIONS/USER_INTERRUPT 终止态
 * 5. L4 摘要委托给 {@link ContextCompressionOrchestrator#summarize}
 *
 * 设计原则：
 * - AgentLoop 不再包含执行逻辑，只做输入验证和 skill 路由
 * - 每增一个 Object 必须对应一个 StateHandler
 * - Handler 是无状态的（通过 ExecutionState 传递每轮状态）
 */
public class WorkflowOrchestrator {

    private static final Logger log = LoggerFactory.getLogger(WorkflowOrchestrator.class);

    private static final int MAX_CACHED_FILES = 20;
    private static final int MAX_FILE_CHARS = 200_000;

    // ==================== 结构性依赖（跨请求） ====================

    private final LLMClient llmClient;
    private final ToolRegistry toolRegistry;
    private final OutputFormatter outputFormatter;
    private final int maxIterations;
    private final long maxExecutionTimeMs;
    private final SystemPromptBuilder promptBuilder;
    private final ContextCompressionOrchestrator compactionPipeline;
    private final TokenBudget tokenBudget;
    private final int llmStreamingTimeoutSeconds;

    // ==================== 文件内容缓存 (LRU, 跨请求) ====================

    private final Map<String, String> fileContentCache;

    // ==================== Handler 映射表 ====================

    private final Map<Object, StateHandler> handlers;
    private final Object initialState;

    // ==================== 构造 ====================

    public WorkflowOrchestrator(LLMClient llmClient, ToolRegistry toolRegistry,
                                OutputFormatter outputFormatter,
                                int maxIterations, int maxExecutionTimeSeconds,
                                int llmStreamingTimeoutSeconds,
                                SystemPromptBuilder promptBuilder,
                                ContextCompressionOrchestrator compactionPipeline,
                                TokenBudget tokenBudget,
                                AgentPattern pattern) {
        this.llmClient = llmClient;
        this.toolRegistry = toolRegistry;
        this.outputFormatter = outputFormatter;
        this.maxIterations = maxIterations;
        this.maxExecutionTimeMs = maxExecutionTimeSeconds > 0 ? maxExecutionTimeSeconds * 1000L : 0;
        this.llmStreamingTimeoutSeconds = llmStreamingTimeoutSeconds;
        this.promptBuilder = promptBuilder;
        this.compactionPipeline = compactionPipeline;
        this.tokenBudget = tokenBudget;
        this.fileContentCache = createFileContentCache();
        this.initialState = pattern.initialState();
        this.handlers = pattern.createHandlers(
            llmClient, toolRegistry, outputFormatter,
            compactionPipeline, tokenBudget, promptBuilder,
            state -> compactionPipeline.summarize(
                state.sessionContext, state.startTime, fileContentCache),
            fileContentCache, llmStreamingTimeoutSeconds);
    }

    // ==================== getter（供 AgentLoop.createSubAgent 使用） ====================

    public LLMClient getLlmClient() { return llmClient; }
    public ToolRegistry getToolRegistry() { return toolRegistry; }
    public OutputFormatter getOutputFormatter() { return outputFormatter; }

    // ==================== 主入口 ====================

    /**
     * 执行 Agent 状态机主循环。
     *
     * @param ctx           会话上下文
     * @param outputHandler 输出流处理器
     * @param startTime     请求开始时间（毫秒）
     * @param safetyChecker 安全检查器（用于 COMPLETE 终止态的输出消毒）
     * @return Agent 执行结果
     */
    public AgentResult execute(SessionContext ctx, Consumer<String> outputHandler,
                                long startTime, SafetyChecker safetyChecker) {
        Object reason = initialState;
        ExecutionState state = new ExecutionState(ctx, outputHandler, startTime, safetyChecker);

        while (true) {
            // 超时检查
            if (isTimeout(startTime)) {
                outputHandler.accept(outputFormatter.formatWarning(
                    "执行超时（" + (maxExecutionTimeMs / 1000) + "秒），已终止"));
                return AgentResult.failure("执行超时");
            }

            // 迭代计数熔断（不覆盖终端态和 ACT 中间态）
            if (state.iterationCount >= maxIterations
                    && reason != TerminalState.COMPLETE
                    && reason != TerminalState.ERROR
                    && reason != ReactState.ACT) {
                reason = TerminalState.MAX_ITERATIONS;
            }

            // 终端态处理（Object 是接口，不能 switch-enum，改用 if-else）
            if (reason == TerminalState.COMPLETE) {
                List<Message> history = ctx.getHistory();
                String lastMsg = history.isEmpty() ? "" : history.get(history.size() - 1).getContent();
                return AgentResult.success(
                    safetyChecker.sanitizeOutput(lastMsg != null ? lastMsg : ""));
            } else if (reason == TerminalState.ERROR
                    || reason == TerminalState.MAX_ITERATIONS
                    || reason == TerminalState.USER_INTERRUPT) {
                return AgentResult.failure("执行终止: " + reason);
            }

            // 分发到对应 Handler
            HandlerResult result = handlers.get(reason).handle(state);

            // 更新迭代计数
            if (result.countTurn()) {
                state.iterationCount++;
            }

            reason = result.nextReason();
        }
    }

    // ==================== 超时 ====================

    private boolean isTimeout(long startTime) {
        return maxExecutionTimeMs > 0
            && (System.currentTimeMillis() - startTime) >= maxExecutionTimeMs;
    }

    // ==================== 文件内容缓存 ====================

    private Map<String, String> createFileContentCache() {
        return Collections.synchronizedMap(
            new LinkedHashMap<String, String>(MAX_CACHED_FILES, 0.75f, true) {
                @Override
                protected boolean removeEldestEntry(Map.Entry<String, String> eldest) {
                    return size() > MAX_CACHED_FILES;
                }
            }
        );
    }

}
