package com.codemind.api.session;

import com.codemind.api.llm.Message;

import java.util.List;

/**
 * 上下文窗口管理器接口
 * 
 * 管理 Agent 的上下文窗口，确保消息列表不超过模型的 token 限制。
 * 
 */
public interface ContextWindowManager {
    
    /**
     * 管理上下文窗口，确保消息列表不超过限制
     * 
     * 如果超过限制，将应用窗口策略（滑动窗口/摘要）
     * 
     * @param messages 当前消息列表
     * @param systemMessage 系统消息（总是保留）
     * @return 处理后的消息列表
     */
    List<Message> manageWindow(List<Message> messages, Message systemMessage);
    
    /**
     * 检查消息列表是否超出上下文窗口
     * 
     * @param messages 消息列表
     * @return true 如果超出限制
     */
    boolean isOverLimit(List<Message> messages);
    
    /**
     * 获取当前消息列表使用的 token 数量
     * 
     * @param messages 消息列表
     * @return token 数量
     */
    int getCurrentTokenCount(List<Message> messages);
    
    /**
     * 获取剩余可用的 token 数量
     * 
     * @param messages 当前消息列表
     * @return 剩余 token 数量
     */
    int getRemainingTokens(List<Message> messages);
    
    /**
     * 设置预留的响应 token 数量
     * 
     * @param tokens 预留的 token 数量
     */
    void setReservedResponseTokens(int tokens);
    
    /**
     * 获取预留的响应 token 数量
     * 
     * @return 预留的 token 数量
     */
    int getReservedResponseTokens();
}
