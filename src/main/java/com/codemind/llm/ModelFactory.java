package com.codemind.llm;

import com.codemind.safety.RateLimiterFactory;
import com.codemind.safety.spi.RateLimiter;

/**
 * 模型工厂
 * 
 * 根据模型配置创建对应的 LLMClient。
 * 
 * 设计原则：
 * - 工厂模式：集中管理 LLMClient 的创建
 * - 装饰器模式：通过 ResilientLLMClient 添加重试和速率限制能力
 * 
 * 使用示例：
 * <pre>
 * LLMClient client = ModelFactory.create(modelConfig);
 * // client 已经是带重试和速率限制的 ResilientLLMClient
 * </pre>
 */
public class ModelFactory {
    
    /** 最大重试次数 */
    private static final int MAX_RETRIES = 3;
    
    /**
     * 根据配置创建 LLMClient
     * 
     * 返回的 LLMClient 已包装 ResilientLLMClient，具备：
     * - API 速率限制（防止 429 错误）
     * - 自动重试（指数退避）
     * - 线程安全
     */
    public static LLMClient create(ModelConfig config) {
        if (config == null) {
            throw new IllegalArgumentException("ModelConfig 不能为空");
        }
        
        String type = config.getType();
        if (type == null || type.isBlank()) {
            throw new IllegalArgumentException(
                "模型配置缺少 type 字段，请在 settings.json 中指定 type");
        }
        
        switch (type) {
            case "openai_compatible":
                OpenAIClient openAIClient = new OpenAIClient(
                    config.getApiKey(),
                    config.getBaseUrl(),
                    config.getDefaultModel(),
                    resolveMaxTokens(config.getDefaultModel()),
                    0.7
                );
                
                // 使用 ResilientLLMClient 包装，添加重试和速率限制
                RateLimiter rateLimiter = RateLimiterFactory.createOpenAIRateLimiter();
                return new ResilientLLMClient(openAIClient, rateLimiter, MAX_RETRIES);
                
            // 以后扩展其他类型
            // case "anthropic":
            //     AnthropicClient anthropicClient = new AnthropicClient(config);
            //     return new ResilientLLMClient(anthropicClient, RateLimiterFactory.createAnthropicRateLimiter(), MAX_RETRIES);
            
            default:
                throw new IllegalArgumentException("不支持的模型类型: " + type);
        }
    }

    /**
     * 根据模型名称动态解析 max_tokens。
     * 现代模型上下文窗口至少 200K tokens，响应预算按窗口比例分配：
     * - 极速/小模型（flash/haiku）: 16K
     * - 中等模型（sonnet/glm-4/deepseek-chat）: 32K
     * - 旗舰模型（opus/gpt-4o/o1）: 64K
     * - plan 生成和重规划可进一步指定更大值（通过调用方传参覆盖）
     */
    private static int resolveMaxTokens(String modelId) {
        if (modelId == null) return 16384;
        String lower = modelId.toLowerCase();
        if (lower.contains("flash") || lower.contains("haiku") || lower.contains("nano")) {
            return 16384;
        }
        if (lower.contains("sonnet") || lower.contains("glm-4") ||
            lower.contains("deepseek-chat") || lower.contains("kimi") ||
            lower.contains("qwq") || lower.contains("qwen2.5")) {
            return 32768;
        }
        if (lower.contains("opus") || lower.contains("gpt-4o") ||
            lower.contains("o1") || lower.contains("claude") ||
            lower.contains("pro") || lower.contains("v3")) {
            return 65536;
        }
        // 默认按中等模型处理
        return 32768;
    }
}
