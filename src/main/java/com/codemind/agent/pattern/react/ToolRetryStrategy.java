package com.codemind.agent.pattern.react;

import com.codemind.tool.ToolResult;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Map;
import java.util.Random;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 工具执行级重试策略。
 *
 * 针对不同类型的工具配置不同的重试策略（Read 可重试 2 次，Bash 重试 1 次）。
 * 使用指数退避 + jitter 避免雪崩。
 *
 * 这个类不感知 AgentLoop 状态机，只负责「执行并重试」这一层。
 * 跨迭代的恢复决策（是否切模型、是否压缩）由 RecoveryManager 处理。
 */
public class ToolRetryStrategy {

    private static final Logger log = LoggerFactory.getLogger(ToolRetryStrategy.class);

    private static final Random RANDOM = new Random();

    /**
     * 每类工具的最大重试次数。
     * 设计上允许运行时通过 {@link #setRetryLimit(String, int)} 动态修改，
     * 因此使用 ConcurrentHashMap（而非不可变 Map）以支持并发配置更新。
     */
    private static final Map<String, Integer> RETRY_LIMITS = new ConcurrentHashMap<>(Map.of(
        "Read", 2,
        "Grep", 2,
        "Glob", 2,
        "WebFetch", 2,
        "Bash", 1,
        "Write", 1,
        "Edit", 1
    ));

    /** 初始退避（毫秒） */
    private static final long INITIAL_BACKOFF_MS = 2000;

    /** 最大退避（毫秒） */
    private static final long MAX_BACKOFF_MS = 30000;

    /**
     * 带重试地执行一个工具。
     *
     * @param toolName  工具名
     * @param executor  实际执行函数的引用（调用方实现）
     * @return 工具执行结果
     */
    public ToolResult executeWithRetry(String toolName, ToolExecutor executor) {
        int maxRetries = RETRY_LIMITS.getOrDefault(toolName, 0);

        for (int attempt = 0; attempt <= maxRetries; attempt++) {
            ToolResult result = executor.execute();

            if (result.isSuccess()) {
                return result;
            }

            if (attempt < maxRetries) {
                // 指数退避 + jitter
                long baseBackoff = Math.min(
                    INITIAL_BACKOFF_MS * (1L << attempt),
                    MAX_BACKOFF_MS
                );
                long jitter = (long) (baseBackoff * 0.25 * RANDOM.nextDouble());
                long sleepMs = baseBackoff + jitter;

                log.warn("工具 {} 执行失败 (attempt {}/{}), {}ms 后重试: {}",
                    toolName, attempt + 1, maxRetries + 1, sleepMs,
                    result.getError() != null ? result.getError() : "未知错误");

                try {
                    Thread.sleep(sleepMs);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    return result;
                }
            }
        }

        // 所有重试耗尽，返回最后一次的结果
        return executor.execute();
    }

    /**
     * 设置某个工具的重试次数。
     */
    public static void setRetryLimit(String toolName, int maxRetries) {
        RETRY_LIMITS.put(toolName, maxRetries);
    }

    /**
     * 工具执行接口。
     */
    @FunctionalInterface
    public interface ToolExecutor {
        ToolResult execute();
    }
}
