package com.codemind.core.service.execution;

import com.codemind.api.cli.OutputFormatter;
import com.codemind.api.llm.*;
import com.codemind.api.session.SessionContext;
import com.codemind.api.tool.ToolRegistry;
import com.codemind.core.*;
import com.codemind.impl.cli.SystemPromptBuilder;
import com.codemind.impl.safety.SafetyChecker;
import com.codemind.impl.session.CompactionPipeline;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.function.Consumer;
import java.util.function.Function;

/**
 * 执行编排器 — AgentLoop 状态机主循环。
 *
 * 职责：
 * 1. 持有全部 Handler 实例和 Handler 映射表
 * 2. 驱动 execute() 主循环：超时检查 → 熔断 → 分发 → 计数
 * 3. 持有跨请求的文件内容缓存 (LRU) 和 compactHistory L4 摘要方法
 * 4. 处理 COMPLETE/ERROR/MAX_ITERATIONS/USER_INTERRUPT 终止态
 *
 * 设计原则：
 * - AgentLoop 不再包含执行逻辑，只做输入验证和 skill 路由
 * - 每增一个 ContinueReason 必须对应一个 StateHandler
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
    private final CompactionPipeline compactionPipeline;
    private final TokenBudget tokenBudget;

    // ==================== 文件内容缓存 (LRU, 跨请求) ====================

    private final Map<String, String> fileContentCache;

    // ==================== Handler 映射表 ====================

    private final Map<ContinueReason, StateHandler> handlers;

    // ==================== 构造 ====================

    public WorkflowOrchestrator(LLMClient llmClient, ToolRegistry toolRegistry,
                                OutputFormatter outputFormatter,
                                int maxIterations, int maxExecutionTimeSeconds,
                                SystemPromptBuilder promptBuilder,
                                CompactionPipeline compactionPipeline,
                                TokenBudget tokenBudget) {
        this.llmClient = llmClient;
        this.toolRegistry = toolRegistry;
        this.outputFormatter = outputFormatter;
        this.maxIterations = maxIterations;
        this.maxExecutionTimeMs = maxExecutionTimeSeconds > 0 ? maxExecutionTimeSeconds * 1000L : 0;
        this.promptBuilder = promptBuilder;
        this.compactionPipeline = compactionPipeline;
        this.tokenBudget = tokenBudget;
        this.fileContentCache = createFileContentCache();
        this.handlers = buildHandlerMap();
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
        ContinueReason reason = ContinueReason.THINK;
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
                    && reason != ContinueReason.COMPLETE
                    && reason != ContinueReason.ERROR
                    && reason != ContinueReason.ACT) {
                reason = ContinueReason.MAX_ITERATIONS;
            }

            // 终端态处理
            switch (reason) {
                case COMPLETE: {
                    List<Message> history = ctx.getHistory();
                    String lastMsg = history.isEmpty() ? "" : history.get(history.size() - 1).getContent();
                    return AgentResult.success(
                        safetyChecker.sanitizeOutput(lastMsg != null ? lastMsg : ""));
                }
                case ERROR:
                case MAX_ITERATIONS:
                case USER_INTERRUPT:
                    return AgentResult.failure("执行终止: " + reason);
                default:
                    break;
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

    // ==================== L4 摘要 (compactHistory) ====================

    /**
     * 全量 L4 摘要 — 供 CompactHandler/LoopBreakHandler/ThinkHandler 调用。
     */
    String compactHistory(SessionContext context, long startTime) {
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
            if (conversation.length() > 500_000) {
                conversation = conversation.substring(conversation.length() - 500_000);
            }

            // 从消息历史中提取 Read 工具的文件内容
            StringBuilder readFilesSection = new StringBuilder();
            readFilesSection.append("\n\n=== Read Files (full contents that must be preserved) ===\n");
            int readCount = 0;
            for (int i = 0; i < messages.size(); i++) {
                Message msg = messages.get(i);
                if (msg.getRole() == Message.Role.ASSISTANT && msg.hasToolCalls()) {
                    for (ToolCall tc : msg.getToolCalls()) {
                        if ("Read".equals(tc.getName())) {
                            String path = tc.getArguments() != null
                                ? (String) tc.getArguments().get("path") : "?";
                            String expectedId = tc.getId();
                            for (int j = i + 1; j < messages.size(); j++) {
                                Message result = messages.get(j);
                                if (result.getRole() == Message.Role.TOOL
                                        && expectedId.equals(result.getToolCallId())) {
                                    readFilesSection.append("\n--- ").append(path).append(" ---\n");
                                    readFilesSection.append(result.getContent()).append("\n");
                                    readCount++;
                                    break;
                                }
                            }
                        }
                    }
                }
            }
            if (readCount == 0) {
                readFilesSection.setLength(0);
            }

            String cachedFiles = getCachedFileContentForPrompt();

            String prompt = "Summarize this coding-agent conversation so work can continue.\n"
                + "Preserve:\n"
                + "1. Current goal / 当前目标\n"
                + "2. Key findings and decisions / 关键发现与决策\n"
                + "3. Files read and their KEY CONTENTS — include ACTUAL SOURCE CODE for each file read (function signatures, class declarations, method bodies, important logic) / 已读文件及其关键内容 — 必须包含实际源代码\n"
                + "4. Files changed and what was modified / 已改文件及修改内容\n"
                + "5. Remaining work / 剩余工作\n"
                + "6. User constraints and preferences / 用户约束\n\n"
                + "IMPORTANT: Your summary MUST include actual source code details for ALL files that were read. "
                + "The agent must be able to continue work WITHOUT re-reading any files. "
                + "Include file paths, class/interface declarations, method signatures, and key logic.\n\n"
                + "At the end of this prompt you will find a section called 'Read Files' or 'Cached File Contents'. "
                + "INCLUDE ALL file contents from that section verbatim in your summary.\n\n"
                + "Conversation:\n" + conversation
                + readFilesSection.toString()
                + cachedFiles;

            // 检查超时余量（至少留 10 秒）
            long elapsed = System.currentTimeMillis() - startTime;
            if (maxExecutionTimeMs > 0 && elapsed > maxExecutionTimeMs - 10_000) {
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

    private String compactForHandler(ExecutionState state) {
        return compactHistory(state.sessionContext, state.startTime);
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

    private String getCachedFileContentForPrompt() {
        if (fileContentCache.isEmpty()) return "";
        StringBuilder sb = new StringBuilder(
            "\n\n=== Read Files (guaranteed full content, do not omit) ===\n");
        synchronized (fileContentCache) {
            for (Map.Entry<String, String> entry : fileContentCache.entrySet()) {
                sb.append("\n--- ").append(entry.getKey()).append(" ---\n");
                sb.append(entry.getValue()).append("\n");
            }
        }
        return sb.toString();
    }

    // ==================== Handler 映射表构建 ====================

    private Map<ContinueReason, StateHandler> buildHandlerMap() {
        Map<ContinueReason, StateHandler> map = new EnumMap<>(ContinueReason.class);

        StopHook stopHook = new StopHook();
        ToolRetryStrategy toolRetryStrategy = new ToolRetryStrategy();

        // 压缩回调 — compactHistory 的方法引用
        Function<ExecutionState, String> compacter = this::compactForHandler;

        // ReAct 核心处理器
        map.put(ContinueReason.THINK, new ThinkHandler(
            llmClient, toolRegistry, outputFormatter, compactionPipeline,
            tokenBudget, stopHook, promptBuilder, compacter));
        map.put(ContinueReason.ACT, new ActHandler(
            outputFormatter, toolRegistry, toolRetryStrategy, fileContentCache));

        // 恢复处理器
        map.put(ContinueReason.RECOVERY_COMPACT, new CompactHandler(compacter));
        map.put(ContinueReason.LOOP_DETECTED, new LoopBreakHandler(outputFormatter, compacter));
        map.put(ContinueReason.TOKEN_BUDGET_CONTINUE, new BudgetHandler(outputFormatter));
        map.put(ContinueReason.RECOVERY_ESCALATE, new EscalateHandler());
        map.put(ContinueReason.CONTINUATION, new ContinuationHandler());
        map.put(ContinueReason.RETRY_BACKOFF, new RetryHandler());
        map.put(ContinueReason.RECOVERY_FAILOVER, new FailoverHandler(outputFormatter));

        // terminal 态由 execute() 自行处理，不在 map 中

        return Collections.unmodifiableMap(map);
    }
}
