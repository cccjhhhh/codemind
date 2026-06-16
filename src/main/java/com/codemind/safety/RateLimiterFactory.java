package com.codemind.safety;

import com.codemind.safety.spi.RateLimiter;

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
    
}