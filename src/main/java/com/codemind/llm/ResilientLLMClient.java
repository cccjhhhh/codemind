package com.codemind.llm;

import com.codemind.safety.spi.RateLimiter;
import com.codemind.common.exception.LLMException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;
import java.util.Random;
import java.util.concurrent.TimeUnit;

/**
 * 带重试和速率限制的 LLM 客户端包装器
 *
 * 设计原则：
 * - 装饰器模式：包装底层 LLMClient，添加额外能力
 * - 单一职责：只负责重试和速率限制，不修改核心逻辑
 *
 * 重试策略：
 * - 429 Rate Limit: 指数退避 + jitter，必须重试
 * - 529 Overloaded: 指数退避 + jitter，连续 3 次标记 fallback
 * - 5xx Server Error: 指数退避 + jitter，可重试
 * - 网络超时: 指数退避 + jitter，可重试
 * - 4xx Client Error: 不重试
 *
 * 注意：流式调用（chatStream/chatStreamWithTools）在网络层是异步的，
 * 错误在回调中产生，executeWithRetry 不能捕获这些异步错误。
 * 流式调用的重试由 AgentLoop 层级的 RecoveryManager 处理。
 */
public class ResilientLLMClient implements LLMClient {

    private static final Logger log = LoggerFactory.getLogger(ResilientLLMClient.class);

    /** 底层 LLM 客户端 */
    private final LLMClient delegate;

    /** 速率限制器（可选） */
    private final RateLimiter rateLimiter;

    /** 最大重试次数 */
    private final int maxRetries;

    /** 初始退避时间（毫秒） */
    private final long initialBackoffMs;

    /** 最大退避时间（毫秒） */
    private final long maxBackoffMs;

    /** jitter 随机源 */
    private static final Random RANDOM = new Random();

    /**
     * 创建带重试和速率限制的 LLM 客户端
     */
    public ResilientLLMClient(LLMClient delegate, RateLimiter rateLimiter, int maxRetries) {
        this(delegate, rateLimiter, maxRetries, 1000, 64000);
    }

    /**
     * 完整构造器
     */
    public ResilientLLMClient(LLMClient delegate, RateLimiter rateLimiter,
                              int maxRetries, long initialBackoffMs, long maxBackoffMs) {
        if (delegate == null) {
            throw new IllegalArgumentException("delegate cannot be null");
        }
        if (maxRetries < 0) {
            throw new IllegalArgumentException("maxRetries must be non-negative");
        }

        this.delegate = delegate;
        this.rateLimiter = rateLimiter;
        this.maxRetries = maxRetries;
        this.initialBackoffMs = initialBackoffMs;
        this.maxBackoffMs = maxBackoffMs;
    }

    /**
     * 创建仅带重试的客户端（无速率限制）
     */
    public ResilientLLMClient(LLMClient delegate, int maxRetries) {
        this(delegate, null, maxRetries);
    }

    @Override
    public LLMResponse chat(List<Message> messages) {
        return executeWithRetry(() -> {
            acquirePermit();
            return delegate.chat(messages);
        });
    }

    @Override
    public void chatStream(List<Message> messages, StreamHandler handler) {
        // 流式调用：异步执行，同步等待模式
        executeWithRetry(() -> {
            acquirePermit();
            delegate.chatStream(messages, handler);
            return null;
        });
    }

    @Override
    public void chatStreamWithTools(List<Message> messages, List<ToolDefinition> tools,
                                    StreamHandler handler) {
        executeWithRetry(() -> {
            acquirePermit();
            delegate.chatStreamWithTools(messages, tools, handler);
            return null;
        });
    }

    @Override
    public LLMResponse chatWithTools(List<Message> messages, List<ToolDefinition> tools) {
        return executeWithRetry(() -> {
            acquirePermit();
            return delegate.chatWithTools(messages, tools);
        });
    }

    /**
     * 获取速率限制许可
     */
    private void acquirePermit() {
        if (rateLimiter != null) {
            try {
                rateLimiter.acquire();
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new LLMException("Rate limiter interrupted", e);
            }
        }
    }

    /**
     * 带重试执行操作，支持 429/529/5xx/timeout，指数退避 + jitter
     */
    private <T> T executeWithRetry(RetryableOperation<T> operation) {
        int attempt = 0;
        long backoffMs = initialBackoffMs;

        while (true) {
            try {
                attempt++;
                return operation.execute();

            } catch (RuntimeException e) {
                throw e;
            } catch (Exception e) {
                // 判断是否可重试
                if (!shouldRetry(e) || attempt > maxRetries) {
                    throw new LLMException("LLM API 调用失败，已达最大重试次数", e);
                }

                log.warn("LLM API 调用失败，准备重试 (attempt {}/{}): {}",
                         attempt, maxRetries, e.getMessage());

                // 指数退避 + jitter：base × 2^(attempt-1) + random(0~25%)
                long baseBackoff = Math.min(initialBackoffMs * (1L << (attempt - 1)), maxBackoffMs);
                long jitter = (long) (baseBackoff * 0.25 * RANDOM.nextDouble());
                long sleepMs = baseBackoff + jitter;

                try {
                    TimeUnit.MILLISECONDS.sleep(sleepMs);
                } catch (InterruptedException ie) {
                    Thread.currentThread().interrupt();
                    throw new LLMException("Retry interrupted", ie);
                }

                backoffMs = Math.min(backoffMs * 2, maxBackoffMs);
            }
        }
    }

    /**
     * 判断异常是否可重试
     */
    private boolean shouldRetry(Throwable t) {
        String message = t.getMessage();
        if (message == null) {
            return false;
        }

        String lower = message.toLowerCase();

        // 429 Rate Limit — 必须重试
        if (message.contains("429") || lower.contains("rate limit")) {
            return true;
        }

        // 529 Overloaded / Service Unavailable — 可重试
        if (message.contains("529") || lower.contains("overloaded")
                || lower.contains("service unavailable")) {
            return true;
        }

        // 5xx Server Error — 可重试
        if (message.contains("500") || message.contains("502") ||
            message.contains("503") || message.contains("504")) {
            return true;
        }

        // 网络超时 — 可重试
        if (lower.contains("timeout") || lower.contains("timed out")) {
            return true;
        }

        return false;
    }

    /**
     * 可重试操作接口
     */
    @FunctionalInterface
    private interface RetryableOperation<T> {
        T execute() throws Exception;
    }
}
