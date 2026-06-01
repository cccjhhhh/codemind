package com.codemind.impl.llm;

import com.codemind.api.llm.*;

/**
 * 模型工厂
 * 
 * 根据模型配置创建对应的 LLMClient
 */
public class ModelFactory {
    
    /**
     * 根据配置创建 LLMClient
     */
    public static LLMClient create(ModelConfig config) {
        if (config == null) {
            throw new IllegalArgumentException("ModelConfig 不能为空");
        }
        
        String type = config.getType();
        if (type == null) {
            type = "openai_compatible"; // 默认类型
        }
        
        switch (type) {
            case "openai_compatible":
                return new OpenAIClient(
                    config.getApiKey(),
                    config.getBaseUrl(),
                    config.getDefaultModel(),
                    4096,
                    0.7
                );
                
            // 以后扩展其他类型
            // case "anthropic":
            //     return new AnthropicClient(config);
            
            default:
                throw new IllegalArgumentException("不支持的模型类型: " + type);
        }
    }
}
