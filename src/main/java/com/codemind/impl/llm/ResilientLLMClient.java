package com.codemind.impl.llm;

import com.codemind.api.llm.LLMClient;
import com.codemind.api.llm.LLMResponse;
import com.codemind.api.llm.Message;
import com.codemind.api.llm.ToolDefinition;
import com.codemind.api.safety.RateLimiter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;
import java.util.concurrent.TimeUnit;

/**
 * 带重试和速率限制的 LLM 客户端包装器
 * 
 * 学习要点：
 * 1. 重试机制：处理临时性错误（429、网络超时）
 * 2. 指数退避：避免连续重试加重服务器负担
 * 3. 速率限制集成：在调用前检查 API 配额
 * 
 * 设计原则：
 * - 装饰器模式：包装底层 LLMClient，添加额外能力
 * - 单一职责：只负责重试和速率限制，不修改核心逻辑
 * 
 * 使用示例：
 * <pre>
 * LLMClient baseClient = new OpenAIClient(apiKey);
 * RateLimiter limiter = RateLimiterFactory.createOpenAIRateLimiter();
 * LLMClient client = new ResilientLLMClient(baseClient, limiter, 3);
 * </pre>
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
    
    /**
     * 创建带重试和速率限制的 LLM 客户端
     * 
     * @param delegate 底层 LLM 客户端
     * @param rateLimiter 速率限制器（可为 null）
     * @param maxRetries 最大重试次数
     */
    public ResilientLLMClient(LLMClient delegate, RateLimiter rateLimiter, int maxRetries) {
        this(delegate, rateLimiter, maxRetries, 1000, 30000);
    }
    
    /**
     * 完整构造器
     * 
     * @param delegate 底层 LLM 客户端
     * @param rateLimiter 速率限制器（可为 null）
     * @param maxRetries 最大重试次数
     * @param initialBackoffMs 初始退避时间（毫秒）
     * @param maxBackoffMs 最大退避时间（毫秒）
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
                throw new RuntimeException("Rate limiter interrupted", e);
            }
        }
    }
    
    /**
     * 带重试执行操作
     * 
     * 重试策略：
     * - 429 (Rate Limit): 必须重试
     * - 5xx (Server Error): 可重试
     * - 网络超时: 可重试
     * - 4xx (Client Error): 不重试
     */
    private <T> T executeWithRetry(RetryableOperation<T> operation) {
        int attempt = 0;
        long backoffMs = initialBackoffMs;
        
        while (true) {
            try {
                attempt++;
                return operation.execute();
                
            } catch (RuntimeException e) {
                // RuntimeException 直接抛出，不重试
                throw e;
            } catch (Exception e) {
                // 判断是否可重试
                if (!shouldRetry(e) || attempt > maxRetries) {
                    throw new RuntimeException("LLM API 调用失败，已达最大重试次数", e);
                }
                
                log.warn("LLM API 调用失败，准备重试 (attempt {}/{}): {}", 
                         attempt, maxRetries, e.getMessage());
                
                // 指数退避等待
                try {
                    TimeUnit.MILLISECONDS.sleep(backoffMs);
                } catch (InterruptedException ie) {
                    Thread.currentThread().interrupt();
                    throw new RuntimeException("Retry interrupted", ie);
                }
                
                // 增加退避时间（指数）
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
        
        // 429 Rate Limit - 必须重试
        if (message.contains("429") || message.contains("rate limit")) {
            return true;
        }
        
        // 5xx Server Error - 可重试
        if (message.contains("500") || message.contains("502") || 
            message.contains("503") || message.contains("504")) {
            return true;
        }
        
        // 网络超时 - 可重试
        if (message.contains("timeout") || message.contains("Timed out")) {
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