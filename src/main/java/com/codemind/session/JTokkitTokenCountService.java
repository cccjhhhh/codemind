package com.codemind.session;

import com.codemind.llm.Message;
import com.codemind.llm.ToolCall;
import com.codemind.session.TokenCountService;
import com.knuddels.jtokkit.Encodings;
import com.knuddels.jtokkit.api.Encoding;
import com.knuddels.jtokkit.api.EncodingRegistry;
import com.knuddels.jtokkit.api.EncodingType;
import com.knuddels.jtokkit.api.ModelType;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * 基于 JTokkit 的专业 Token 计数服务
 * 
 * JTokkit 是 OpenAI tiktoken 的 Java 移植版，提供精确的 token 计数。
 * 
 * 支持的模型（通过 JTokkit ModelType）：
 * - GPT-4, GPT-4-turbo, GPT-4-32K (cl100k_base)
 * - GPT-4o, GPT-4o-mini (o200k_base)
 * - GPT-3.5-turbo 系列 (cl100k_base)
 * - text-embedding-ada-002 等
 * 
 * 对于其他模型（DeepSeek, Claude 等）：
 * - DeepSeek: 使用 cl100k_base（与 OpenAI API 兼容）
 * - Claude: 使用 cl100k_base 近似（Claude 有自己的 tokenizer）
 */
public class JTokkitTokenCountService implements TokenCountService {
    
    private static final Logger LOGGER = LoggerFactory.getLogger(JTokkitTokenCountService.class);
    
    // 单例 EncodingRegistry（线程安全，创建成本高）
    private static final EncodingRegistry REGISTRY = Encodings.newDefaultEncodingRegistry();
    
    // 默认编码类型（用于未知模型）
    private static final EncodingType DEFAULT_ENCODING = EncodingType.CL100K_BASE;
    
    // 默认上下文窗口（用于未知模型）
    private static final int DEFAULT_MAX_CONTEXT = 8192;
    
    // DeepSeek 等兼容模型的上下文窗口
    private static final Map<String, Integer> COMPATIBLE_MODEL_CONTEXT = Map.of(
        "deepseek-chat", 64000,
        "deepseek-coder", 16000,
        "claude-3-opus", 200000,
        "claude-3-sonnet", 200000,
        "claude-3-haiku", 200000,
        "claude-3-5-sonnet", 200000
    );
    
    // 每条消息的格式开销（ChatML 格式）
    // 参考：https://cookbook.openai.com/examples/how_to_count_tokens_with_tiktoken
    private static final int MESSAGE_FORMAT_OVERHEAD = 4;
    
    // 消息开始和结束的特殊 token
    private static final int MESSAGE_START_TOKENS = 3;  // <|im_start|>{role}\n
    private static final int MESSAGE_END_TOKENS = 1;     // <|im_end|>
    
    // 工具调用的额外开销
    private static final int TOOL_CALL_OVERHEAD = 10;
    
    // 当前使用的编码
    private final Encoding encoding;
    
    // 当前使用的编码类型
    private final EncodingType encodingType;
    
    // 最大上下文窗口
    private final int maxContextTokens;
    
    // 模型名称（用于日志）
    private final String modelName;
    
    /**
     * 使用默认编码类型（cl100k_base）
     */
    public JTokkitTokenCountService() {
        this(DEFAULT_ENCODING, DEFAULT_MAX_CONTEXT, "default");
    }
    
    /**
     * 使用指定的编码类型
     */
    public JTokkitTokenCountService(EncodingType encodingType, int maxContextTokens) {
        this(encodingType, maxContextTokens, "custom");
    }
    
    /**
     * 内部构造器
     */
    private JTokkitTokenCountService(EncodingType encodingType, int maxContextTokens, String modelName) {
        this.encodingType = encodingType;
        this.encoding = REGISTRY.getEncoding(encodingType);
        this.maxContextTokens = maxContextTokens;
        this.modelName = modelName;
    }
    
    /**
     * 根据模型名称创建 Token 计数服务
     * 
     * 处理策略：
     * 1. 首先尝试 JTokkit 内置的 ModelType.fromName()
     * 2. 如果失败，检查是否是已知的兼容模型（DeepSeek, Claude）
     * 3. 最后使用默认编码，并输出警告日志
     * 
     * @param modelName 模型名称（如 "gpt-4o", "deepseek-chat"）
     * @return Token 计数服务实例
     */
    public static JTokkitTokenCountService forModel(String modelName) {
        if (modelName == null || modelName.isEmpty()) {
            LOGGER.warn("模型名称为空，使用默认编码 cl100k_base");
            return new JTokkitTokenCountService(DEFAULT_ENCODING, DEFAULT_MAX_CONTEXT, "unknown");
        }
        
        // 1. 尝试 JTokkit 内置的 ModelType
        Optional<ModelType> modelType = ModelType.fromName(modelName);
        if (modelType.isPresent()) {
            ModelType mt = modelType.get();
            LOGGER.info("使用 JTokkit 内置模型配置: " + modelName + 
                       " -> encoding=" + mt.getEncodingType() + 
                       ", maxContext=" + mt.getMaxContextLength());
            return new JTokkitTokenCountService(
                mt.getEncodingType(), 
                mt.getMaxContextLength(), 
                modelName
            );
        }
        
        // 2. 尝试通过 EncodingRegistry 的 getEncodingForModel（支持部分模型别名）
        Optional<Encoding> encodingOpt = REGISTRY.getEncodingForModel(modelName);
        if (encodingOpt.isPresent()) {
            // 获取到了编码，但需要估算上下文窗口
            int maxContext = COMPATIBLE_MODEL_CONTEXT.getOrDefault(
                modelName.toLowerCase(), 
                DEFAULT_MAX_CONTEXT
            );
            LOGGER.info("通过 EncodingRegistry 获取编码: " + modelName + 
                       " (使用估算的上下文窗口: " + maxContext + ")");
            // 这里需要获取对应的 EncodingType
            EncodingType encodingType = inferEncodingType(modelName);
            return new JTokkitTokenCountService(encodingType, maxContext, modelName);
        }
        
        // 3. 检查是否是已知的兼容模型
        String lowerModelName = modelName.toLowerCase();
        if (lowerModelName.contains("deepseek")) {
            int maxContext = COMPATIBLE_MODEL_CONTEXT.getOrDefault(lowerModelName, 64000);
            LOGGER.info("DeepSeek 模型使用 cl100k_base 编码: " + modelName);
            return new JTokkitTokenCountService(EncodingType.CL100K_BASE, maxContext, modelName);
        }
        
        if (lowerModelName.contains("claude")) {
            int maxContext = COMPATIBLE_MODEL_CONTEXT.getOrDefault(lowerModelName, 200000);
            LOGGER.warn("Claude 模型使用 cl100k_base 近似编码（Claude 有自己的 tokenizer）: " + modelName);
            return new JTokkitTokenCountService(EncodingType.CL100K_BASE, maxContext, modelName);
        }
        
        // 4. 未知模型，使用默认编码并警告
        LOGGER.warn("未知模型 '" + modelName + "'，使用默认编码 cl100k_base。" +
                       "如果 token 计数不准确，请手动配置 EncodingType。");
        return new JTokkitTokenCountService(DEFAULT_ENCODING, DEFAULT_MAX_CONTEXT, modelName);
    }
    
    /**
     * 根据模型名称推断编码类型
     */
    private static EncodingType inferEncodingType(String modelName) {
        String lower = modelName.toLowerCase();
        
        // GPT-4o 系列使用 o200k_base
        if (lower.contains("gpt-4o") || lower.contains("gpt-4.1") || lower.contains("o1")) {
            return EncodingType.O200K_BASE;
        }
        
        // 其他 GPT 系列和 DeepSeek 使用 cl100k_base
        return EncodingType.CL100K_BASE;
    }
    
    @Override
    public int estimateTokens(Message message) {
        int tokens = MESSAGE_START_TOKENS;
        
        // 角色名称
        tokens += encoding.countTokens(message.getRole().name().toLowerCase());
        
        // 消息内容
        if (message.getContent() != null) {
            tokens += encoding.countTokens(message.getContent());
        }
        
        // 工具调用 ID（针对 TOOL 角色的消息）
        if (message.getToolCallId() != null) {
            tokens += encoding.countTokens(message.getToolCallId());
        }
        
        // 工具调用列表（针对 ASSISTANT 角色的消息）
        if (message.hasToolCalls()) {
            for (ToolCall toolCall : message.getToolCalls()) {
                tokens += TOOL_CALL_OVERHEAD;
                tokens += encoding.countTokens(toolCall.getName());
                tokens += encoding.countTokens(formatArguments(toolCall.getArguments()));
            }
        }
        
        tokens += MESSAGE_END_TOKENS;
        tokens += MESSAGE_FORMAT_OVERHEAD;
        
        return tokens;
    }
    
    @Override
    public int estimateTokens(List<Message> messages) {
        int total = 0;
        for (Message message : messages) {
            total += estimateTokens(message);
        }
        
        // 消息列表的额外开销（priming）
        total += 3;
        
        return total;
    }
    
    @Override
    public int estimateTextTokens(String text) {
        if (text == null || text.isEmpty()) {
            return 0;
        }
        return encoding.countTokens(text);
    }
    
    @Override
    public int getMaxContextTokens() {
        return maxContextTokens;
    }
    
    @Override
    public int getAvailableContextTokens(int reservedForResponse) {
        return Math.max(0, maxContextTokens - reservedForResponse);
    }
    
    /**
     * 格式化参数 Map 为字符串
     */
    private String formatArguments(Map<String, Object> arguments) {
        if (arguments == null || arguments.isEmpty()) {
            return "{}";
        }
        
        StringBuilder sb = new StringBuilder();
        sb.append("{");
        boolean first = true;
        for (Map.Entry<String, Object> entry : arguments.entrySet()) {
            if (!first) {
                sb.append(", ");
            }
            sb.append("\"").append(entry.getKey()).append("\": ");
            Object value = entry.getValue();
            if (value instanceof String) {
                sb.append("\"").append(value).append("\"");
            } else {
                sb.append(value);
            }
            first = false;
        }
        sb.append("}");
        return sb.toString();
    }
}