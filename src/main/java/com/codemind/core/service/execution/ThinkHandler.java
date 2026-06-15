package com.codemind.core.service.execution;

import com.codemind.api.cli.OutputFormatter;
import com.codemind.api.llm.*;
import com.codemind.api.session.SessionContext;
import com.codemind.api.tool.ToolRegistry;
import com.codemind.core.*;
import com.codemind.impl.cli.SystemPromptBuilder;
import com.codemind.impl.session.CompactionPipeline;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;
import java.util.function.Function;

/**
 * THINK 处理器 — LLM 推理 + 工具决策。
 *
 * ReAct 的 Think 阶段：
 * 1. 构建 system prompt + 执行 L1-L3 压缩管线
 * 2. Token 预算检查 → L4 摘要
 * 3. LLM 流式调用 → 获取回复 + 工具决策
 * 4. 空结果 / max_tokens 截断处理
 * 5. StopHook 检查 → COMPLETE
 * 6. 循环检测 → LOOP_DETECTED
 * 7. 选定工具存入 ExecutionState.pendingToolCalls → ACT
 */
public class ThinkHandler implements StateHandler {

    private static final Logger log = LoggerFactory.getLogger(ThinkHandler.class);

    private final LLMClient llmClient;
    private final ToolRegistry toolRegistry;
    private final OutputFormatter outputFormatter;
    private final CompactionPipeline compactionPipeline;
    private final TokenBudget tokenBudget;
    private final StopHook stopHook;
    private final SystemPromptBuilder promptBuilder;
    private final Function<ExecutionState, String> compacter;
    private final int llmStreamingTimeoutSeconds;

    public ThinkHandler(LLMClient llmClient, ToolRegistry toolRegistry,
                        OutputFormatter outputFormatter,
                        CompactionPipeline compactionPipeline,
                        TokenBudget tokenBudget, StopHook stopHook,
                        SystemPromptBuilder promptBuilder,
                        Function<ExecutionState, String> compacter,
                        int llmStreamingTimeoutSeconds) {
        this.llmClient = llmClient;
        this.toolRegistry = toolRegistry;
        this.outputFormatter = outputFormatter;
        this.compactionPipeline = compactionPipeline;
        this.tokenBudget = tokenBudget;
        this.stopHook = stopHook;
        this.promptBuilder = promptBuilder;
        this.compacter = compacter;
        this.llmStreamingTimeoutSeconds = llmStreamingTimeoutSeconds;
    }

    @Override
    public HandlerResult handle(ExecutionState state) {
        state.pendingToolCalls = null;
        ContinueReason reason = think(state);
        // THINK（无工具）或 ACT（有工具）都计为一次完整迭代
        boolean countTurn = reason == ContinueReason.THINK || reason == ContinueReason.ACT;
        return new HandlerResult(reason, countTurn);
    }

    // ==================== THINK 核心 ====================

    private ContinueReason think(ExecutionState state) {
        SessionContext ctx = state.sessionContext;
        Consumer<String> outputHandler = state.outputHandler;

        // 1. 构建 system prompt
        if (promptBuilder != null) {
            ctx.setSystemMessage(promptBuilder.build(ctx));
        }

        // 2. 获取原始消息并执行压缩管线（单入口）
        List<Message> messages = ctx.getHistory();
        if (compactionPipeline != null) {
            messages = compactionPipeline.run(messages);
        }
        if (ctx.getSystemMessage() != null) {
            messages = new ArrayList<>(messages);
            messages.add(0, ctx.getSystemMessage());
        }

        // 2a. Token 预算检查 → L4 摘要
        if (tokenBudget != null && tokenBudget.needsCompact(messages)) {
            ContinueReason budgetReason = handleTokenBudget(state);
            if (budgetReason != null) return budgetReason;
            messages = ctx.getHistory();
            if (ctx.getSystemMessage() != null) {
                messages = new ArrayList<>(messages);
                messages.add(0, ctx.getSystemMessage());
            }
        }

        // 3. 获取工具定义
        List<ToolDefinition> tools = toolRegistry.getDefinitionsForSkill(ctx.getActiveSkill());

        // 4. 输出 thinking 提示
        outputHandler.accept(outputFormatter.formatThinkingStart());

        // 5. LLM 流式调用
        AgentTurnResult turnResult = runSingleTurn(messages, tools, outputHandler);

        // 6. 空结果处理
        if (!turnResult.hasToolCalls() && isEmpty(turnResult.fullText)) {
            log.warn("LLM 返回空结果");
            if (state.recoveryManager.hasAttemptedCompact()) {
                log.error("RECOVERY_COMPACT 后 LLM 仍然返回空结果");
                return ContinueReason.ERROR;
            }
            state.recoveryManager.setAttemptedCompact(true);
            return ContinueReason.RECOVERY_COMPACT;
        }

        // 7. max_tokens 截断检测
        if ("length".equals(turnResult.stopReason)) {
            log.info("LLM 输出被 max_tokens 截断");
            if (turnResult.hasToolCalls() || !isEmpty(turnResult.fullText)) {
                addToHistory(ctx, turnResult);
            }
            if (state.recoveryManager.escalateMaxTokens()) {
                return ContinueReason.RECOVERY_ESCALATE;
            }
            return ContinueReason.CONTINUATION;
        }

        // 8. 注入到 context
        addToHistory(ctx, turnResult);

        // 9. 无工具调用 → 评估是否完成
        if (!turnResult.hasToolCalls()) {
            LLMResponse response = new LLMResponse(
                turnResult.fullText != null ? turnResult.fullText : "",
                List.of(), true, 0);
            ContinueReason evalResult = stopHook.evaluate(response, false);
            return evalResult != null ? evalResult : ContinueReason.THINK;
        }

        // 10. 记录工具调用 + 循环检测
        for (ToolCall tc : turnResult.toolCalls) {
            ContinueReason loopReason = state.recoveryManager.recordToolCall(
                tc.getName(), tc.getArguments());
            if (loopReason == ContinueReason.LOOP_DETECTED) {
                ctx.addMessage(Message.user(
                    "【系统提示】检测到重复操作模式，请尝试不同的方法完成当前任务。"));
                return ContinueReason.LOOP_DETECTED;
            }
        }

        // 11. 存入 pendingToolCalls，由 ACT 阶段执行
        state.pendingToolCalls = turnResult.toolCalls;
        return ContinueReason.ACT;
    }

    // ==================== Token 预算 ====================

    private ContinueReason handleTokenBudget(ExecutionState state) {
        log.info("Token 预算紧张，触发 L4 摘要");
        try {
            String summary = compacter.apply(state);
            if (summary != null && !summary.isEmpty()) {
                state.sessionContext.clearHistory();
                state.sessionContext.addMessage(Message.user("[Compacted]\n\n" + summary));
                if (compactionPipeline != null) compactionPipeline.resetFailures();
            } else if (tokenBudget.needsCompact(state.sessionContext.getHistory())) {
                return ContinueReason.TOKEN_BUDGET_CONTINUE;
            }
        } catch (Exception e) {
            log.warn("L4 摘要异常: {}", e.getMessage());
            if (tokenBudget.needsCompact(state.sessionContext.getHistory())) {
                return ContinueReason.TOKEN_BUDGET_CONTINUE;
            }
        }
        return null;
    }

    // ==================== LLM 调用 ====================

    private AgentTurnResult runSingleTurn(List<Message> history, List<ToolDefinition> tools,
                                          Consumer<String> outputHandler) {
        int maxAttempts = 2;

        for (int attempt = 1; attempt <= maxAttempts; attempt++) {
            CountDownLatch latch = new CountDownLatch(1);
            AtomicReference<String> fullText = new AtomicReference<>("");
            AtomicReference<List<ToolCall>> toolCalls = new AtomicReference<>(new ArrayList<>());
            AtomicReference<String> stopReason = new AtomicReference<>(null);
            AtomicReference<Exception> error = new AtomicReference<>(null);
            AtomicReference<Boolean> firstDelta = new AtomicReference<>(true);
            AtomicBoolean cancelled = new AtomicBoolean(false);

            llmClient.chatStreamWithTools(history, tools, new LLMClient.StreamHandler() {
                @Override
                public void onEvent(StreamEvent event) {
                    if (cancelled.get()) return;
                    switch (event.getType()) {
                        case TEXT_DELTA:
                            if (firstDelta.compareAndSet(true, false)) {
                                outputHandler.accept(outputFormatter.formatThinkingEnd());
                            }
                            outputHandler.accept(event.getTextDelta());
                            break;
                        case MESSAGE_COMPLETE:
                            fullText.set(event.getFullText());
                            toolCalls.set(event.getToolCalls() != null ? event.getToolCalls() : new ArrayList<>());
                            stopReason.set(event.getFinishReason());
                            latch.countDown();
                            break;
                        case ERROR:
                            error.set(event.getError());
                            latch.countDown();
                            break;
                        default:
                            break;
                    }
                }

                @Override
                public void onError(Exception e) {
                    if (cancelled.get()) return;
                    error.set(e);
                    latch.countDown();
                }
            });

            try {
                if (!latch.await(llmStreamingTimeoutSeconds, TimeUnit.SECONDS)) {
                    cancelled.set(true);
                    if (attempt < maxAttempts) {
                        log.warn("LLM 流式响应超时（attempt {}/{}），重试中...", attempt, maxAttempts);
                        continue;
                    }
                    log.error("LLM 流式响应超时（attempt {}/{}），放弃", attempt, maxAttempts);
                    return new AgentTurnResult("", new ArrayList<>());
                }
            } catch (InterruptedException e) {
                cancelled.set(true);
                Thread.currentThread().interrupt();
                return new AgentTurnResult("", new ArrayList<>());
            }

            if (error.get() != null) {
                cancelled.set(true);
                Exception ex = error.get();
                if (isContextLengthError(ex)) {
                    log.warn("上下文溢出，需要压缩后重试: {}", ex.getMessage());
                    return new AgentTurnResult("", new ArrayList<>());
                }
                if (attempt < maxAttempts && shouldRetryLLMError(ex)) {
                    log.warn("LLM 调用失败（attempt {}/{}），重试中: {}",
                        attempt, maxAttempts, ex.getMessage());
                    try { Thread.sleep(1000L * attempt); } catch (InterruptedException ie) {
                        Thread.currentThread().interrupt();
                        return new AgentTurnResult("", new ArrayList<>());
                    }
                    continue;
                }
                log.error("LLM 调用失败: {} - {}", ex.getClass().getName(), ex.getMessage());
                return new AgentTurnResult("", new ArrayList<>());
            }

            return new AgentTurnResult(fullText.get(), toolCalls.get(), stopReason.get());
        }

        return new AgentTurnResult("", new ArrayList<>());
    }

    // ==================== 辅助方法 ====================

    private void addToHistory(SessionContext context, AgentTurnResult turnResult) {
        if (turnResult.hasToolCalls()) {
            context.addMessage(Message.assistantWithTools(
                turnResult.fullText, turnResult.toolCalls));
        } else {
            context.addMessage(Message.assistant(turnResult.fullText));
        }
    }

    private static boolean isEmpty(String s) {
        return s == null || s.isEmpty();
    }

    private static boolean isContextLengthError(Throwable t) {
        if (t == null) return false;
        String msg = t.getMessage();
        if (msg != null && (msg.contains("prompt_too_long")
            || msg.contains("context_length_exceeded")
            || msg.contains("maximum context length"))) {
            return true;
        }
        return isContextLengthError(t.getCause());
    }

    private static boolean shouldRetryLLMError(Exception e) {
        String msg = e.getMessage();
        if (msg == null) return false;
        String lower = msg.toLowerCase();
        return msg.contains("429") || msg.contains("529")
            || lower.contains("rate limit")
            || lower.contains("overloaded")
            || lower.contains("service unavailable")
            || msg.contains("500") || msg.contains("502")
            || msg.contains("503") || msg.contains("504")
            || lower.contains("timeout")
            || lower.contains("timed out")
            || lower.contains("eof")
            || lower.contains("reset");
    }

    // ==================== 内部类 ====================

    private static class AgentTurnResult {
        final String fullText;
        final List<ToolCall> toolCalls;
        final String stopReason;

        AgentTurnResult(String fullText, List<ToolCall> toolCalls, String stopReason) {
            this.fullText = fullText;
            this.toolCalls = toolCalls;
            this.stopReason = stopReason;
        }

        AgentTurnResult(String fullText, List<ToolCall> toolCalls) {
            this(fullText, toolCalls, null);
        }

        boolean hasToolCalls() {
            return toolCalls != null && !toolCalls.isEmpty();
        }
    }
}
