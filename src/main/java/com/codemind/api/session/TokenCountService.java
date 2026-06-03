package com.codemind.api.session;

import com.codemind.api.llm.Message;

import java.util.List;

/**
 * Token 计数服务接口
 * 
 * 用于估算消息列表的 token 数量。
 * 学习要点：上下文窗口管理、Token 计数策略
 * 
 * 注意：这是一个估算服务，精确计数需要使用模型特定的 tokenizer。
 */
public interface TokenCountService {
    
    /**
     * 估算单条消息的 token 数量
     * 
     * @param message 消息
     * @return 估算的 token 数量
     */
    int estimateTokens(Message message);
    
    /**
     * 估算消息列表的总 token 数量
     * 
     * @param messages 消息列表
     * @return 估算的总 token 数量
     */
    int estimateTokens(List<Message> messages);
    
    /**
     * 估算文本的 token 数量
     * 
     * @param text 文本内容
     * @return 估算的 token 数量
     */
    int estimateTextTokens(String text);
    
    /**
     * 获取模型的最大上下文窗口大小
     * 
     * @return 最大 token 数量
     */
    int getMaxContextTokens();
    
    /**
     * 计算可用于消息的 token 数量
     * （总上下文减去预留给响应的 token）
     * 
     * @param reservedForResponse 预留给响应的 token 数量
     * @return 可用于消息的 token 数量
     */
    int getAvailableContextTokens(int reservedForResponse);
}
