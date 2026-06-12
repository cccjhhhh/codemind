package com.codemind.core;

import com.codemind.api.cli.OutputFormatter;
import com.codemind.api.llm.*;
import com.codemind.api.safety.PermissionGate;
import com.codemind.api.session.SessionContext;
import com.codemind.api.tool.ToolRegistry;
import com.codemind.api.tool.ToolResult;
import com.codemind.dto.skill.SkillRouteDto;
import com.codemind.impl.cli.SystemPromptBuilder;
import com.codemind.impl.safety.SafetyChecker;
import com.codemind.impl.session.CompactionPipeline;
import com.codemind.impl.skill.routing.SkillRouter;
import com.codemind.impl.mcp.McpToolRegistry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;

public class AgentLoop {

    private static final Logger log = LoggerFactory.getLogger(AgentLoop.class);

    private final LLMClient llmClient;
    private final ToolRegistry toolRegistry;
    private final PermissionGate permissionGate;
    private final OutputFormatter outputFormatter;
    private final int maxIterations;
    private final long maxExecutionTimeMs;
    private final SkillRouter skillRouter;
    private final SystemPromptBuilder promptBuilder;
    private final CompactionPipeline compactionPipeline;
    private final TokenBudget tokenBudget;
    private final StopHook stopHook;
    private final ToolRetryStrategy toolRetryStrategy;
    private final McpToolRegistry mcpToolRegistry;

    private int iterationCount = 0;
    private RecoveryManager recoveryManager = new RecoveryManager();

    // 旧 8-参数构造器（保持向后兼容）
    public AgentLoop(LLMClient llmClient, ToolRegistry toolRegistry,
                     PermissionGate permissionGate, OutputFormatter outputFormatter,
                     int maxIterations, int maxExecutionTimeSeconds,
                     SkillRouter skillRouter, SystemPromptBuilder promptBuilder) {
        this(llmClient, toolRegistry, permissionGate, outputFormatter,
             maxIterations, maxExecutionTimeSeconds, skillRouter, promptBuilder,
             null, null, null);
    }

    // 新 10-参数构造器（推荐）
    public AgentLoop(LLMClient llmClient, ToolRegistry toolRegistry,
                     PermissionGate permissionGate, OutputFormatter outputFormatter,
                     int maxIterations, int maxExecutionTimeSeconds,
                     SkillRouter skillRouter, SystemPromptBuilder promptBuilder,
                     CompactionPipeline compactionPipeline, TokenBudget tokenBudget,
                     McpToolRegistry mcpToolRegistry) {
        this.llmClient = llmClient;
        this.toolRegistry = toolRegistry;
        this.permissionGate = permissionGate;
        this.outputFormatter = outputFormatter;
        this.maxIterations = maxIterations;
        this.maxExecutionTimeMs = maxExecutionTimeSeconds > 0 ? maxExecutionTimeSeconds * 1000L : 0;
        this.skillRouter = skillRouter;
        this.promptBuilder = promptBuilder;
        this.compactionPipeline = compactionPipeline;
        this.tokenBudget = tokenBudget;
        this.mcpToolRegistry = mcpToolRegistry;
        this.stopHook = new StopHook();
        this.toolRetryStrategy = new ToolRetryStrategy();
    }

    // ==================== 公共入口 ====================

    public AgentResult runStream(String input, SessionContext context,
                                 Consumer<String> outputHandler) {
        try {
            long startTime = System.currentTimeMillis();
            iterationCount = 0;
            recoveryManager.reset();

            SafetyChecker safetyChecker = new SafetyChecker();

            AgentResult safetyResult = validateInput(input, safetyChecker);
            if (safetyResult != null) return safetyResult;

            if (context.hasActiveSkill()) context.clearActiveSkill();
            tryActivateSkill(input, context);

            context.addMessage(Message.user(input));
            return executeLoop(context, outputHandler, safetyChecker, startTime);

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

    // ==================== 主循环（状态机） ====================

    private AgentResult executeLoop(SessionContext context,
                                    Consumer<String> outputHandler,
                                    SafetyChecker safetyChecker,
                                    long startTime) {
        ContinueReason reason = ContinueReason.NEXT_TURN;

        while (true) {
            // 超时检查
            if (isTimeout(startTime)) {
                outputHandler.accept(outputFormatter.formatWarning(
                    "执行超时（" + (maxExecutionTimeMs / 1000) + "秒），已终止"));
                return AgentResult.failure("执行超时");
            }

            // 迭代计数熔断 → 路由到 switch 统一处理
            if (iterationCount >= maxIterations
                    && reason != ContinueReason.COMPLETE
                    && reason != ContinueReason.ERROR) {
                reason = ContinueReason.MAX_ITERATIONS;
            }

            switch (reason) {
                case COMPLETE: {
                    List<Message> history = context.getHistory();
                    String lastMsg = history.isEmpty() ? "" : history.get(history.size() - 1).getContent();
                    return AgentResult.success(safetyChecker.sanitizeOutput(lastMsg != null ? lastMsg : ""));
                }

                case ERROR:
                case MAX_ITERATIONS:
                case USER_INTERRUPT:
                    return AgentResult.failure("执行终止: " + reason);

                case RECOVERY_COMPACT: {
                    // LLM 全量摘要（L4）
                    try {
                        String summary = compactHistory(context, startTime);
                        // 验证摘要质量
                        if (summary != null && !summary.isEmpty() 
                            && !summary.contains("object hashes")
                            && !summary.contains("Unable to determine")
                            && summary.length() > 50) {
                            context.clearHistory();
                            context.addMessage(Message.user("[Compacted]\n\n" + summary));
                            log.info("RECOVERY_COMPACT: 全量摘要完成, length={}", summary.length());
                            recoveryManager.setAttemptedCompact(false);
                        } else {
                            log.warn("RECOVERY_COMPACT: 摘要质量不合格, 跳过压缩");
                        }
                    } catch (Exception e) {
                        log.error("RECOVERY_COMPACT 失败: {}", e.getMessage());
                        if (compactionPipeline != null && compactionPipeline.getConsecutiveFailures() >= 3) {
                            return AgentResult.failure("上下文压缩失败，无法继续");
                        }
                    }
                    reason = ContinueReason.NEXT_TURN;
                    continue;
                }

                case RECOVERY_ESCALATE:
                    // max_tokens 已由 RecoveryManager 升级，重新发起查询
                    log.info("RECOVERY_ESCALATE: max_tokens -> {} (stage {})",
                        recoveryManager.getCurrentMaxTokens(), recoveryManager.getMaxTokensStage());
                    iterationCount++;
                    reason = ContinueReason.NEXT_TURN;
                    continue;

                case RECOVERY_FAILOVER:
                    // 切换到 fallback 模型后重试
                    recoveryManager.switchToFallback();
                    outputHandler.accept(outputFormatter.formatWarning(
                        "连续失败，切换 fallback 模型: " + recoveryManager.isUsingFallback()));
                    // 清空连续失败计数器
                    context.setVariable("_consecutiveFailures", 0);
                    iterationCount++;
                    reason = ContinueReason.NEXT_TURN;
                    continue;

                case RETRY_BACKOFF:
                    // 瞬态错误（429/529）：指数退避后重试
                    try {
                        long delay = Math.min(1000L * (1L << recoveryManager.getConsecutive529()), 32000L);
                        log.info("瞬态错误退避 {}ms", delay);
                        Thread.sleep(delay);
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                        return AgentResult.failure("中断");
                    }
                    // 不增加 iterationCount — 重试同一轮
                    reason = ContinueReason.NEXT_TURN;
                    continue;

                case CONTINUATION:
                    // max_tokens 截断续写：追加续写提示
                    if (recoveryManager.recordContinuation()) {
                        context.addMessage(Message.user(
                            "【续写】输出被截断，请直接从中断处继续，不要道歉或总结。"));
                        // 记录为一轮迭代（LLM 实际产出了内容，只是截断了）
                        iterationCount++;
                        reason = ContinueReason.NEXT_TURN;
                        continue;
                    }
                    // 续写额度耗尽，退回正常判断
                    reason = ContinueReason.NEXT_TURN;
                    continue;

                case LOOP_DETECTED:
                    // 检测到循环模式：强制触发摘要打断
                    log.warn("[LoopBreak] 循环检测触发，开始压缩上下文");
                    outputHandler.accept(outputFormatter.formatWarning("检测到重复操作模式，触发上下文压缩"));
                    try {
                        // 清空 recentToolCalls 避免立即再次触发
                        recoveryManager.clearRecentToolCalls();
                        String summary = compactHistory(context, startTime);
                        
                        // 验证摘要质量：必须包含实际内容，不能是占位符或空内容
                        if (summary != null && !summary.isEmpty() 
                            && !summary.contains("object hashes")
                            && !summary.contains("Unable to determine")
                            && summary.length() > 50) {
                            log.info("[LoopBreak] 压缩摘要长度: {} 字符", summary.length());
                            log.debug("[LoopBreak] 压缩摘要内容: {}", summary.substring(0, Math.min(500, summary.length())));
                            context.clearHistory();
                            context.addMessage(Message.user("[Compacted — loop break]\n\n" + summary));
                            recoveryManager.setAttemptedCompact(false);
                        } else {
                            // 摘要质量不合格，不压缩，直接终止循环
                            log.warn("[LoopBreak] 压缩摘要质量不合格(length={}, content={}), 直接终止循环", 
                                summary != null ? summary.length() : 0,
                                summary != null ? summary.substring(0, Math.min(100, summary.length())) : "null");
                            return AgentResult.failure("检测到循环且无法生成有效摘要，任务终止");
                        }
                    } catch (Exception e) {
                        log.error("LOOP_DETECTED 压缩失败: {}", e.getMessage());
                    }
                    reason = ContinueReason.NEXT_TURN;
                    continue;

                case TOKEN_BUDGET_CONTINUE:
                    // token 预算压缩失败：加提示让 LLM 精简回复
                    outputHandler.accept(outputFormatter.formatWarning(
                        "Token 预算紧张，请控制回复长度"));
                    context.addMessage(Message.user(
                        "【Token 预算警告】上下文空间不足，请尽量精简回复，省略无关细节。"));
                    iterationCount++;
                    reason = ContinueReason.NEXT_TURN;
                    continue;

                case NEXT_TURN:
                    ContinueReason newReason = queryLoop(context, outputHandler, startTime);
                    if (newReason == ContinueReason.NEXT_TURN) {
                        iterationCount++;
                    }
                    reason = newReason;
                    continue;
            }
        }
    }

    private ContinueReason queryLoop(SessionContext context,
                                     Consumer<String> outputHandler,
                                     long startTime) {
        // 1. 构建 system prompt
        if (promptBuilder != null) {
            String prompt = promptBuilder.build(context);
            context.setSystemMessage(prompt);
        }

        // 2. 获取历史并执行压缩管线
        List<Message> messages = context.getManagedHistory();

        // 2a. 管线预压缩（L3→L1→L2）
        if (compactionPipeline != null) {
            messages = compactionPipeline.run(messages, null);
        }

        // 2b. 检查 token 预算，触发 L4 摘要
        if (tokenBudget != null && tokenBudget.needsCompact(messages)) {
            log.info("Token 预算紧张，触发 L4 摘要");
            boolean compactOk = false;
            try {
                context.clearHistory();
                for (Message msg : messages) context.addMessage(msg);
                String summary = compactHistory(context, startTime);
                if (summary != null && !summary.isEmpty()) {
                    context.clearHistory();
                    context.addMessage(Message.user("[Compacted]\n\n" + summary));
                    messages = context.getManagedHistory();
                    if (compactionPipeline != null) compactionPipeline.resetFailures();
                    compactOk = true;
                }
            } catch (Exception e) {
                log.warn("L4 摘要失败: {}", e.getMessage());
            }
            // 压缩失败且预算仍紧张 → 让 LLM 提前收到警告，精简回复
            if (!compactOk && tokenBudget.needsCompact(messages)) {
                return ContinueReason.TOKEN_BUDGET_CONTINUE;
            }
        }

        // 3. 获取工具列表
        List<ToolDefinition> tools = toolRegistry.getDefinitionsForSkill(context.getActiveSkill());

        // 4. 输出 thinking 提示
        outputHandler.accept(outputFormatter.formatThinkingStart());

        // 5. 调 LLM API
        AgentTurnResult turnResult = runSingleTurn(messages, tools, outputHandler);

        // 6. 检查是否 ContextLengthException → RECOVERY_COMPACT
        // (错误已经在 runSingleTurn 中被捕获并返回空结果，我们在空结果时检查是否有 compact 需求)

        // 7. 空结果处理
        if (!turnResult.hasToolCalls() && (turnResult.fullText == null || turnResult.fullText.isEmpty())) {
            log.warn("LLM 返回空结果");
            // 已尝试过 compact 但再次失败 → 错误
            if (recoveryManager.hasAttemptedCompact()) {
                log.error("RECOVERY_COMPACT 后 LLM 仍然返回空结果");
                return ContinueReason.ERROR;
            }
            recoveryManager.setAttemptedCompact(true);
            return ContinueReason.RECOVERY_COMPACT;
        }

        // 7a. max_tokens 截断检测 → 路由到 RECOVERY_ESCALATE / CONTINUATION
        if ("length".equals(turnResult.stopReason)) {
            log.info("LLM 输出被 max_tokens 截断");
            // 如果还有文本内容或工具调用，先把它们注入 context
            if (turnResult.hasToolCalls() || (turnResult.fullText != null && !turnResult.fullText.isEmpty())) {
                addToHistory(context, turnResult);
            }
            // 未升级过则路由到 RECOVERY_ESCALATE
            if (recoveryManager.escalateMaxTokens()) {
                return ContinueReason.RECOVERY_ESCALATE;
            }
            // 已达最高级 → 续写
            return ContinueReason.CONTINUATION;
        }

        // 8. 注入到 context
        addToHistory(context, turnResult);

        // 9. 无工具调用 → 评估是否完成
        if (!turnResult.hasToolCalls()) {
            LLMResponse response = new LLMResponse(
                turnResult.fullText != null ? turnResult.fullText : "",
                List.of(),
                true,
                0
            );
            ContinueReason evalResult = stopHook.evaluate(response, false);
            return evalResult != null ? evalResult : ContinueReason.NEXT_TURN;
        }

        // 10. 记录工具调用 + 循环检测
        for (ToolCall tc : turnResult.toolCalls) {
            ContinueReason loopReason = recoveryManager.recordToolCall(tc.getName(), tc.getArguments());
            if (loopReason == ContinueReason.LOOP_DETECTED) {
                context.addMessage(Message.user(
                    "【系统提示】检测到重复操作模式，请尝试不同的方法完成当前任务。"));
                return ContinueReason.LOOP_DETECTED;
            }
        }

        // 11. 执行工具（使用 ToolPartitioner 分区，ToolRetryStrategy 重试）
        List<List<ToolCall>> batches = ToolPartitioner.partition(turnResult.toolCalls);
        boolean allFailed = true;

        for (List<ToolCall> batch : batches) {
            boolean batchFailed;
            if (ToolPartitioner.isParallel(batch)) {
                batchFailed = executeBatchParallel(batch, context, outputHandler);
            } else {
                batchFailed = executeBatchSerial(batch, context, outputHandler);
            }
            if (!batchFailed) allFailed = false;
        }

        // 12. 连续失败提示 / fallover 切换
        if (allFailed) {
            int failCount = context.getVariable("_consecutiveFailures") instanceof Integer
                ? (int) context.getVariable("_consecutiveFailures") + 1 : 1;
            context.setVariable("_consecutiveFailures", failCount);
            if (failCount >= 5) {
                // 连续 5 轮全失败 → 路由到 RECOVERY_FAILOVER 尝试切模型
                log.warn("连续 {} 轮工具全部失败，触发 failover", failCount);
                return ContinueReason.RECOVERY_FAILOVER;
            }
            if (failCount == 3) {
                // 连续 3 轮失败，先发提示
                String hint = "【系统提示】连续 3 轮工具调用全部失败。请检查失败原因后重试。";
                context.addMessage(Message.user(hint));
                context.setVariable("_consecutiveFailures", 0);
            }
        } else {
            context.setVariable("_consecutiveFailures", 0);
        }

        // 13. 恢复 reactive compact 标记 + 记录成功轮次
        recoveryManager.setAttemptedCompact(false);
        recoveryManager.onSuccessfulTurn();

        // 14. 返回继续
        return ContinueReason.NEXT_TURN;
    }

    // ==================== 工具执行 ====================

    private boolean executeBatchSerial(List<ToolCall> batch, SessionContext context,
                                       Consumer<String> outputHandler) {
        boolean allFailed = true;
        for (ToolCall tc : batch) {
            ToolResult result = executeSingleTool(tc, outputHandler);
            addToolResultToHistory(context, tc, result);
            if (result.isSuccess()) allFailed = false;
        }
        return allFailed;
    }

    private boolean executeBatchParallel(List<ToolCall> batch, SessionContext context,
                                         Consumer<String> outputHandler) {
        ExecutorService executor = Executors.newFixedThreadPool(
            Math.min(batch.size(), 5));
        List<Future<ToolResult>> futures = new ArrayList<>();

        for (ToolCall tc : batch) {
            futures.add(executor.submit(() -> {
                outputHandler.accept(outputFormatter.formatToolCallStart(tc.getName(), tc.getArguments()));
                ToolResult result;
                if (mcpToolRegistry != null && mcpToolRegistry.hasTool(tc.getName())) {
                    result = mcpToolRegistry.executeTool(tc.getName(), tc.getArguments());
                } else {
                    result = toolRegistry.execute(tc.getName(), tc.getArguments());
                }
                outputHandler.accept(outputFormatter.formatToolCallEnd(tc.getName(), result));
                return result;
            }));
        }

        executor.shutdown();
        try {
            executor.awaitTermination(60, TimeUnit.SECONDS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }

        boolean allFailed = true;
        for (int i = 0; i < batch.size(); i++) {
            try {
                ToolResult result = futures.get(i).get(1, TimeUnit.SECONDS);
                ToolCall tc = batch.get(i);
                addToolResultToHistory(context, tc, result);
                if (result.isSuccess()) allFailed = false;
            } catch (Exception e) {
                log.warn("并行工具执行失败: {}", e.getMessage());
                addToolResultToHistory(context, batch.get(i), ToolResult.failure(e.getMessage()));
            }
        }
        return allFailed;
    }

    private ToolResult executeSingleTool(ToolCall toolCall, Consumer<String> outputHandler) {
        outputHandler.accept(outputFormatter.formatToolCallStart(
            toolCall.getName(), toolCall.getArguments()));
        ToolResult result = toolRetryStrategy.executeWithRetry(
            toolCall.getName(),
            () -> {
                if (mcpToolRegistry != null && mcpToolRegistry.hasTool(toolCall.getName())) {
                    return mcpToolRegistry.executeTool(toolCall.getName(), toolCall.getArguments());
                }
                return toolRegistry.execute(toolCall.getName(), toolCall.getArguments());
            }
        );
        outputHandler.accept(outputFormatter.formatToolCallEnd(toolCall.getName(), result));
        return result;
    }

    private void addToolResultToHistory(SessionContext context, ToolCall tc, ToolResult result) {
        String content = result.isSuccess() ? result.getOutput() : "Error: " + result.getError();
        context.addMessage(Message.tool(content, tc.getId()));
    }

    // ==================== LLM 调用 ====================

    private AgentTurnResult runSingleTurn(List<Message> history, List<ToolDefinition> tools,
                                          Consumer<String> outputHandler) {
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
            if (!latch.await(30, TimeUnit.SECONDS)) {
                cancelled.set(true);
                log.error("LLM 流式响应超时（30s）");
                return new AgentTurnResult("", new ArrayList<>());
            }
        } catch (InterruptedException e) {
            cancelled.set(true);
            Thread.currentThread().interrupt();
            return new AgentTurnResult("", new ArrayList<>());
        }

        if (error.get() != null) {
            cancelled.set(true);
            // 检查是否为上下文溢出错误
            Exception ex = error.get();
            if (isContextLengthError(ex)) {
                log.warn("上下文溢出，需要压缩后重试: {}", ex.getMessage());
                return new AgentTurnResult("", new ArrayList<>());
            }
            log.error("LLM 调用失败: {} - {}", ex.getClass().getName(), ex.getMessage());
            return new AgentTurnResult("", new ArrayList<>());
        }

        return new AgentTurnResult(fullText.get(), toolCalls.get(), stopReason.get());
    }

    private boolean isContextLengthError(Throwable t) {
        if (t == null) return false;
        String msg = t.getMessage();
        if (msg != null && (msg.contains("prompt_too_long")
            || msg.contains("context_length_exceeded")
            || msg.contains("maximum context length"))) {
            return true;
        }
        return isContextLengthError(t.getCause());
    }

    // ==================== L4 compactHistory ====================

    private String compactHistory(SessionContext context, long startTime) {
        try {
            List<Message> messages = context.getManagedHistory();
            if (messages.isEmpty()) return null;

            // 保存 transcript
            if (compactionPipeline != null) {
                Path transcriptDir = Path.of(".codemind", "transcripts");
                Files.createDirectories(transcriptDir);
                String ts = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss"));
                Path transcriptPath = transcriptDir.resolve(ts + ".jsonl");
                Files.writeString(transcriptPath, messages.toString());
            }

            // 构建摘要 prompt
            String conversation = messages.toString();
            if (conversation.length() > 80000) conversation = conversation.substring(0, 80000);

            String prompt = "Summarize this coding-agent conversation so work can continue.\n"
                + "Preserve:\n"
                + "1. Current goal / 当前目标\n"
                + "2. Key findings and decisions / 关键发现与决策\n"
                + "3. Files read and changed / 已读改文件列表\n"
                + "4. Remaining work / 剩余工作\n"
                + "5. User constraints and preferences / 用户约束\n\n"
                + "Conversation:\n" + conversation;

            // 检查超时余量（至少留 10 秒）
            long elapsed = System.currentTimeMillis() - startTime;
            if (maxExecutionTimeMs > 0 && elapsed > maxExecutionTimeMs - 10000) {
                log.warn("执行时间不足，跳过 L4 摘要");
                return null;
            }

            LLMResponse response = llmClient.chat(List.of(
                Message.system("You are a conversation summarizer."),
                Message.user(prompt)
            ));

            if (response.getContent() == null || response.getContent().isBlank()) {
                log.warn("L4 摘要返回空");
                return null;
            }

            return response.getContent();

        } catch (Exception e) {
            log.error("L4 摘要失败: {}", e.getMessage());
            return null;
        }
    }

    // ==================== 辅助方法 ====================

    private boolean isTimeout(long startTime) {
        return maxExecutionTimeMs > 0
            && (System.currentTimeMillis() - startTime) >= maxExecutionTimeMs;
    }

    private void addToHistory(SessionContext context, AgentTurnResult turnResult) {
        if (turnResult.hasToolCalls()) {
            context.addMessage(Message.assistantWithTools(turnResult.fullText, turnResult.toolCalls));
        } else {
            context.addMessage(Message.assistant(turnResult.fullText));
        }
    }

    // ==================== 子 Agent ====================

    public AgentLoop createSubAgent() {
        return new AgentLoop(
            this.llmClient, this.toolRegistry, this.permissionGate, this.outputFormatter,
            15, 60, null, null, null, null, null
        );
    }

    // ==================== 内部类 ====================

    private static class AgentTurnResult {
        final String fullText;
        final List<ToolCall> toolCalls;
        final String stopReason;  // "stop" | "length" | "tool_calls" | null
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
