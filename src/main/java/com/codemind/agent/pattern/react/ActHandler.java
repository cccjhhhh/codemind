package com.codemind.agent.pattern.react;

import com.codemind.agent.statemachine.HandlerResult;
import com.codemind.agent.engine.ExecutionState;
import com.codemind.agent.statemachine.pattern.ReactState;
import com.codemind.agent.statemachine.StateHandler;

import com.codemind.agent.statemachine.ContinueReason;
import com.codemind.agent.engine.ToolPartitioner;
import com.codemind.frontend.output.spi.OutputFormatter;
import com.codemind.llm.Message;
import com.codemind.llm.ToolCall;
import com.codemind.session.SessionContext;
import com.codemind.tool.ToolRegistry;
import com.codemind.tool.ToolResult;
import com.codemind.agent.async.ThreadPoolConfig;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.function.Consumer;

import java.util.*;
import java.util.concurrent.Callable;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

/**
 * ACT 处理器 — 执行 THINK 阶段选定的工具。
 *
 * ReAct 的 Act 阶段：
 * 1. 读取 ExecutionState.pendingToolCalls
 * 2. 分区并行/串行执行工具
 * 3. 注入 Tool Result 到会话历史
 * 4. 缓存 Read 结果到文件缓存
 * 5. 连续失败检测 → RECOVERY_FAILOVER
 * 6. 重置 recovery 标记 → THINK
 */
public class ActHandler implements StateHandler {

    private static final Logger log = LoggerFactory.getLogger(ActHandler.class);
    private static final int MAX_FILE_CHARS = 200_000;

    private final OutputFormatter outputFormatter;
    private final ToolRegistry toolRegistry;
    private final ToolRetryStrategy toolRetryStrategy;
    private final Map<String, String> fileContentCache;

    public ActHandler(OutputFormatter outputFormatter,
                      ToolRegistry toolRegistry,
                      ToolRetryStrategy toolRetryStrategy,
                      Map<String, String> fileContentCache) {
        this.outputFormatter = outputFormatter;
        this.toolRegistry = toolRegistry;
        this.toolRetryStrategy = toolRetryStrategy;
        this.fileContentCache = fileContentCache;
    }

    @Override
    public HandlerResult handle(ExecutionState state) {
        List<ToolCall> toolCalls = state.pendingToolCalls;
        state.pendingToolCalls = null;

        // 没有待执行工具（防御性处理），返回 THINK
        if (toolCalls == null || toolCalls.isEmpty()) {
            return HandlerResult.withCount(ReactState.THINK);
        }

        // 1. 分区执行
        List<List<ToolCall>> batches = ToolPartitioner.partition(toolCalls);
        boolean allFailed = true;

        for (List<ToolCall> batch : batches) {
            boolean batchFailed;
            if (ToolPartitioner.isParallel(batch)) {
                batchFailed = executeBatchParallel(batch, state);
            } else {
                batchFailed = executeBatchSerial(batch, state);
            }
            if (!batchFailed) allFailed = false;
        }

        // 2. 连续失败检测
        SessionContext ctx = state.sessionContext;
        if (allFailed) {
            int failCount = ctx.getVariable("_consecutiveFailures") instanceof Integer
                ? (int) ctx.getVariable("_consecutiveFailures") + 1 : 1;
            ctx.setVariable("_consecutiveFailures", failCount);
            if (failCount >= 5) {
                log.warn("连续 {} 轮工具全部失败，触发 failover", failCount);
                return HandlerResult.withoutCount(ContinueReason.RECOVERY_FAILOVER);
            }
            if (failCount == 3) {
                ctx.addMessage(Message.user(
                    "【系统提示】连续 3 轮工具调用全部失败。请检查失败原因后重试。"));
                ctx.setVariable("_consecutiveFailures", 0);
            }
        } else {
            ctx.setVariable("_consecutiveFailures", 0);
        }

        // 3. 重置 recovery 标记 + 记录成功轮次
        state.recoveryManager.setAttemptedCompact(false);
        state.recoveryManager.onSuccessfulTurn();

        return HandlerResult.withCount(ReactState.THINK);
    }

    // ==================== 串行执行 ====================

    private boolean executeBatchSerial(List<ToolCall> batch, ExecutionState state) {
        boolean allFailed = true;
        for (ToolCall tc : batch) {
            ToolResult result = executeSingleTool(tc, state.outputHandler);
            addToolResultToHistory(state.sessionContext, tc, result);
            cacheReadResult(tc, result);
            if (result.isSuccess()) allFailed = false;
        }
        return allFailed;
    }

    // ==================== 并行执行 ====================

    private boolean executeBatchParallel(List<ToolCall> batch, ExecutionState state) {
        List<Callable<ToolResult>> tasks = new ArrayList<>();
        for (ToolCall tc : batch) {
            tasks.add(() -> {
                state.outputHandler.accept(outputFormatter.formatToolCallStart(
                    tc.getName(), tc.getArguments()));
                ToolResult result = toolRegistry.execute(tc.getName(), tc.getArguments());
                state.outputHandler.accept(outputFormatter.formatToolCallEnd(
                    tc.getName(), result));
                return result;
            });
        }

        List<Future<ToolResult>> futures;
        try {
            futures = ThreadPoolConfig.TOOL_EXEC.invokeAll(tasks);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return true;
        }

        boolean allFailed = true;
        for (int i = 0; i < batch.size(); i++) {
            try {
                ToolResult result = futures.get(i).get(1, TimeUnit.SECONDS);
                ToolCall tc = batch.get(i);
                addToolResultToHistory(state.sessionContext, tc, result);
                cacheReadResult(tc, result);
                if (result.isSuccess()) allFailed = false;
            } catch (Exception e) {
                log.warn("并行工具执行失败: {}", e.getMessage());
                addToolResultToHistory(state.sessionContext, batch.get(i),
                    ToolResult.failure(e.getMessage()));
            }
        }
        return allFailed;
    }

    // ==================== 单工具执行 ====================

    private ToolResult executeSingleTool(ToolCall toolCall, Consumer<String> outputHandler) {
        outputHandler.accept(outputFormatter.formatToolCallStart(
            toolCall.getName(), toolCall.getArguments()));
        ToolResult result = toolRetryStrategy.executeWithRetry(
            toolCall.getName(),
            () -> toolRegistry.execute(toolCall.getName(), toolCall.getArguments())
        );
        outputHandler.accept(outputFormatter.formatToolCallEnd(
            toolCall.getName(), result));
        return result;
    }

    // ==================== 辅助方法 ====================

    private void addToolResultToHistory(SessionContext context, ToolCall tc, ToolResult result) {
        String content = result.isSuccess()
            ? result.getOutput()
            : "Error: " + result.getError();
        context.addMessage(Message.tool(content, tc.getId()));
    }

    private void cacheReadResult(ToolCall tc, ToolResult result) {
        if (!result.isSuccess()) return;
        String name = tc.getName();
        if ("Read".equals(name)) {
            String path = tc.getArguments() != null
                ? (String) tc.getArguments().get("path") : null;
            if (path == null) return;
            String content = result.getOutput();
            if (content == null) return;
            if (content.length() > MAX_FILE_CHARS) {
                content = content.substring(0, MAX_FILE_CHARS) + "\n... [truncated]";
            }
            fileContentCache.put(path, content);
        } else if ("Grep".equals(name)) {
            String pattern = tc.getArguments() != null
                ? (String) tc.getArguments().get("pattern") : "";
            String grepPath = tc.getArguments() != null
                ? (String) tc.getArguments().get("path") : "";
            String key = "grep:" + pattern + ":" + grepPath;
            String content = result.getOutput();
            if (content == null) return;
            if (content.length() > MAX_FILE_CHARS) {
                content = content.substring(0, MAX_FILE_CHARS) + "\n... [truncated]";
            }
            fileContentCache.put(key, content);
        }
    }
}
