package com.codemind.impl.session;

import com.codemind.api.llm.Message;
import com.codemind.api.session.ContextWindowManager;
import com.codemind.api.session.TokenCountService;

import java.util.ArrayList;
import java.util.List;

/**
 * 上下文窗口管理器实现
 * 
 * 采用滑动窗口策略：
 * 1. 始终保留系统消息（如果有）
 * 2. 当超出限制时，从最早的用户消息开始删除
 * 3. 保持消息的成对完整性（user-assistant 交替）
 * 
 * - Token 预算的动态管理
 */
public class SlidingWindowContextManager implements ContextWindowManager {
    
    // 默认预留的响应 token 数量
    private static final int DEFAULT_RESERVED_TOKENS = 1024;
    
    // Token 计数服务
    private final TokenCountService tokenCountService;
    
    // 预留的响应 token 数量
    private int reservedResponseTokens;
    
    /**
     * 使用 JTokkit Token 计数服务（推荐）
     */
    public SlidingWindowContextManager() {
        this(new JTokkitTokenCountService());
    }
    
    /**
     * 指定 Token 计数服务
     * 
     * @param tokenCountService Token 计数服务
     */
    public SlidingWindowContextManager(TokenCountService tokenCountService) {
        this.tokenCountService = tokenCountService;
        this.reservedResponseTokens = DEFAULT_RESERVED_TOKENS;
    }
    
    /**
     * 为指定模型创建上下文窗口管理器
     * 
     * @param modelId 模型标识符（如 "gpt-4o", "deepseek-chat"）
     * @return 配置好的上下文窗口管理器
     */
    public static SlidingWindowContextManager forModel(String modelId) {
        return new SlidingWindowContextManager(JTokkitTokenCountService.forModel(modelId));
    }
    
    @Override
    public List<Message> manageWindow(List<Message> messages, Message systemMessage) {
        List<Message> result = new ArrayList<>();
        
        // 1. 添加系统消息（始终保留在开头）
        if (systemMessage != null) {
            result.add(systemMessage);
        }
        
        // 2. 添加其他消息，检查是否超出限制
        for (Message msg : messages) {
            // 跳过原有的系统消息（如果有）
            if (msg.getRole() == Message.Role.SYSTEM) {
                continue;
            }
            
            result.add(msg);
            
            // 检查是否超出限制
            if (isOverLimit(result)) {
                // 超出限制，需要裁剪
                result = trimMessages(result, systemMessage != null);
                break;
            }
        }
        
        return result;
    }
    
    @Override
    public boolean isOverLimit(List<Message> messages) {
        int tokens = getCurrentTokenCount(messages);
        return tokens > tokenCountService.getAvailableContextTokens(reservedResponseTokens);
    }
    
    @Override
    public int getCurrentTokenCount(List<Message> messages) {
        return tokenCountService.estimateTokens(messages);
    }
    
    @Override
    public int getRemainingTokens(List<Message> messages) {
        int used = getCurrentTokenCount(messages);
        int available = tokenCountService.getAvailableContextTokens(reservedResponseTokens);
        return Math.max(0, available - used);
    }
    
    @Override
    public void setReservedResponseTokens(int tokens) {
        this.reservedResponseTokens = tokens;
    }
    
    @Override
    public int getReservedResponseTokens() {
        return reservedResponseTokens;
    }
    
    /**
     * 裁剪消息列表
     * 
     * 策略：保留系统消息，删除最早的用户/助手消息对
     * 使用迭代而非递归，避免 StackOverflow
     * 
     * @param messages 消息列表
     * @param hasSystemMessage 是否有系统消息
     * @return 裁剪后的消息列表
     */
    private List<Message> trimMessages(List<Message> messages, boolean hasSystemMessage) {
        List<Message> result = new ArrayList<>(messages);
        
        // 使用迭代，避免递归导致的 StackOverflow
        int maxIterations = 100;  // 安全限制
        int iteration = 0;
        
        while (isOverLimit(result) && result.size() > (hasSystemMessage ? 2 : 1) && iteration < maxIterations) {
            iteration++;
            
            // 找到第一个可以删除的消息对
            int deleteIndex = -1;
            int startIndex = hasSystemMessage && result.get(0).getRole() == Message.Role.SYSTEM ? 1 : 0;
            
            // 找到第一个 user 消息
            for (int i = startIndex; i < result.size(); i++) {
                if (result.get(i).getRole() == Message.Role.USER) {
                    deleteIndex = i;
                    break;
                }
            }
            
            if (deleteIndex == -1) {
                // 没有找到 user 消息，无法继续裁剪
                break;
            }
            
            // 删除 user 消息
            result.remove(deleteIndex);

            // 如果下一条是 assistant 或 tool，也删除
            if (deleteIndex < result.size()) {
                Message.Role nextRole = result.get(deleteIndex).getRole();
                if (nextRole == Message.Role.ASSISTANT) {
                    // 删除 assistant 消息
                    result.remove(deleteIndex);
                    // 同时删除属于该 assistant 的所有 tool 结果（防止 tool 孤立）
                    while (deleteIndex < result.size() && result.get(deleteIndex).getRole() == Message.Role.TOOL) {
                        result.remove(deleteIndex);
                    }
                } else if (nextRole == Message.Role.TOOL) {
                    // 删除所有连续 tool（防止孤立 tool）
                    while (deleteIndex < result.size() && result.get(deleteIndex).getRole() == Message.Role.TOOL) {
                        result.remove(deleteIndex);
                    }
                }
            }
        }
        
        if (iteration >= maxIterations) {
            System.err.println("警告: 上下文裁剪达到最大迭代次数，可能存在配置问题");
        }
        
        return result;
    }
}
