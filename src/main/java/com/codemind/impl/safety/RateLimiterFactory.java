package com.codemind.impl.safety;

import com.codemind.api.safety.RateLimiter;

/**
 * API 速率限制器工厂
 * 
 * 为不同的 API 提供商创建合适的速率限制器。
 */
public class RateLimiterFactory {
    
    /**
     * OpenAI API 速率限制器
     * 
     * OpenAI 不同模型的限制不同：
     * - GPT-4: 约 500 请求/分钟（batch）或 200 请求/分钟（minute)
     * - GPT-3.5: 约 3500 请求/分钟
     * 
     * 这里使用保守配置：100 请求/分钟，最大突发 50
     */
    public static RateLimiter createOpenAIRateLimiter() {
        // 100 请求/分钟 = 1.67 请求/秒
        // 最大突发 50（允许短时间内的突发流量）
        return new TokenBucketRateLimiter(2, 50);
    }
    
    /**
     * Anthropic API 速率限制器
     * 
     * Claude 的限制通常更宽松，使用：
     * - 50 请求/分钟
     * - 最大突发 30
     */
    public static RateLimiter createAnthropicRateLimiter() {
        return new TokenBucketRateLimiter(1, 30);
    }
    
    /**
     * 创建通用 API 速率限制器
     * 
     * @param requestsPerMinute 每分钟允许的请求数
     * @return 配置好的 RateLimiter
     */
    public static RateLimiter createGeneric(long requestsPerMinute) {
        long permitsPerSecond = Math.max(1, requestsPerMinute / 60);
        long maxBurst = Math.max(requestsPerMinute / 10, 10);
        return new TokenBucketRateLimiter(permitsPerSecond, maxBurst);
    }
}