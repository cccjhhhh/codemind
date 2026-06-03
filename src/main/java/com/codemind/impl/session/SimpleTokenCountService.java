package com.codemind.impl.session;

import com.codemind.api.llm.Message;
import com.codemind.api.llm.ToolCall;
import com.codemind.api.session.TokenCountService;

import java.util.List;

/**
 * 简单的 Token 计数服务实现
 * 
 * 使用启发式方法估算 token 数量：
 * - 英文/数字：约 4 字符 = 1 token
 * - 中日韩字符：约 2 字符 = 1 token（CJK 字符通常编码为多个 token）
 * - 每条消息额外增加格式开销 token
 * 
 * 学习要点：Token 估算策略、上下文窗口管理
 * 
 * 参考：OpenAI 的 tiktoken 库、Claude 的 tokenizer
 */
public class SimpleTokenCountService implements TokenCountService {
    
    // 默认上下文窗口大小（GPT-4 级别）
    private static final int DEFAULT_MAX_CONTEXT = 8192;
    
    // 每条消息的格式开销（role、结构等）
    private static final int MESSAGE_OVERHEAD = 4;
    
    // 工具调用的额外开销
    private static final int TOOL_CALL_OVERHEAD = 10;
    
    // 最大上下文窗口
    private final int maxContextTokens;
    
    /**
     * 使用默认上下文窗口大小
     */
    public SimpleTokenCountService() {
        this(DEFAULT_MAX_CONTEXT);
    }
    
    /**
     * 指定上下文窗口大小
     * 
     * @param maxContextTokens 最大 token 数量
     */
    public SimpleTokenCountService(int maxContextTokens) {
        this.maxContextTokens = maxContextTokens;
    }
    
    @Override
    public int estimateTokens(Message message) {
        int tokens = MESSAGE_OVERHEAD;
        
        // 角色名称
        tokens += estimateTextTokens(message.getRole().name().toLowerCase());
        
        // 内容
        if (message.getContent() != null) {
            tokens += estimateTextTokens(message.getContent());
        }
        
        // 工具调用 ID
        if (message.getToolCallId() != null) {
            tokens += estimateTextTokens(message.getToolCallId());
        }
        
        // 工具调用列表
        if (message.hasToolCalls()) {
            for (ToolCall toolCall : message.getToolCalls()) {
                tokens += TOOL_CALL_OVERHEAD;
                tokens += estimateTextTokens(toolCall.getName());
                tokens += estimateTextTokens(toolCall.getArguments().toString());
            }
        }
        
        return tokens;
    }
    
    @Override
    public int estimateTokens(List<Message> messages) {
        int total = 0;
        for (Message message : messages) {
            total += estimateTokens(message);
        }
        // 额外的消息列表开销
        total += 3;
        return total;
    }
    
    @Override
    public int estimateTextTokens(String text) {
        if (text == null || text.isEmpty()) {
            return 0;
        }
        
        int tokens = 0;
        int cjkChars = 0;
        int otherChars = 0;
        
        // 使用代码点迭代，支持supplementary characters
        int offset = 0;
        while (offset < text.length()) {
            int codePoint = text.codePointAt(offset);
            if (isCJKCodePoint(codePoint)) {
                cjkChars++;
            } else {
                otherChars++;
            }
            offset += Character.charCount(codePoint);
        }
        
        // CJK 字符：约 2 字符 = 1 token
        tokens += cjkChars / 2 + (cjkChars % 2 > 0 ? 1 : 0);
        
        // 其他字符：约 4 字符 = 1 token
        tokens += otherChars / 4 + (otherChars % 4 > 0 ? 1 : 0);
        
        return Math.max(1, tokens);
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
     * 判断是否为中日韩字符
     * 
     * 使用代码点（code point）判断，支持supplementary planes
     */
    private boolean isCJKCharacter(char c) {
        int codePoint = Character.codePointAt(new char[]{c}, 0);
        return isCJKCodePoint(codePoint);
    }
    
    /**
     * 使用代码点判断是否为 CJK 字符
     */
    private boolean isCJKCodePoint(int codePoint) {
        // CJK Unified Ideographs (4E00-9FFF)
        if (codePoint >= 0x4E00 && codePoint <= 0x9FFF) return true;
        // CJK Unified Ideographs Extension A (3400-4DBF)
        if (codePoint >= 0x3400 && codePoint <= 0x4DBF) return true;
        // CJK Symbols and Punctuation (3000-303F)
        if (codePoint >= 0x3000 && codePoint <= 0x303F) return true;
        // Hiragana (3040-309F)
        if (codePoint >= 0x3040 && codePoint <= 0x309F) return true;
        // Katakana (30A0-30FF)
        if (codePoint >= 0x30A0 && codePoint <= 0x30FF) return true;
        // Hangul Syllables (AC00-D7AF)
        if (codePoint >= 0xAC00 && codePoint <= 0xD7AF) return true;
        // Full-width ASCII variants (FF00-FFEF)
        if (codePoint >= 0xFF00 && codePoint <= 0xFFEF) return true;
        
        return false;
    }
}
