package com.codemind.api.session;

import com.codemind.api.llm.Message;

import java.nio.file.Path;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import com.codemind.impl.skill.SkillDefinition;

/**
 * 会话上下文
 * 
 * 存储当前会话的状态信息，包括：
 * - 工作目录
 * - 对话历史（短期记忆）
 * - 系统消息
 * - 变量存储
 * 
 * 学习要点：上下文管理、状态持久化、短期记忆实现
 */
public class SessionContext {
    
    // 默认最大历史消息数量（防止内存无限增长）
    private static final int DEFAULT_MAX_HISTORY_SIZE = 100;
    
    private final String sessionId;
    private Path workingDirectory;
    private final List<Message> history;
    private final Map<String, Object> variables;
    private LocalDateTime createdAt;
    private LocalDateTime lastActiveAt;
    
    // 最大历史消息数量
    private int maxHistorySize = DEFAULT_MAX_HISTORY_SIZE;
    
    // 系统消息（独立存储，不在 history 中）
    private Message systemMessage;
    
    // 上下文窗口管理器（可选）
    private ContextWindowManager contextWindowManager;

    // 当前活跃技能（用于在 AgentLoop 和 SystemPromptBuilder 之间共享状态）
    private SkillDefinition activeSkill;
    
    public SessionContext(String sessionId) {
        this.sessionId = sessionId;
        this.workingDirectory = Path.of(System.getProperty("user.dir"));
        this.history = new ArrayList<>();
        this.variables = new HashMap<>();
        this.createdAt = LocalDateTime.now();
        this.lastActiveAt = LocalDateTime.now();
    }
    
    /**
     * 添加消息到历史记录
     * 
     * 当历史消息数量超过限制时，自动删除最早的消息（保留系统消息）
     */
    public void addMessage(Message message) {
        history.add(message);
        lastActiveAt = LocalDateTime.now();
        
        // 检查容量限制，超出时删除最早的消息
        while (history.size() > maxHistorySize) {
            history.remove(0);
        }
    }
    
    /**
     * 设置最大历史消息数量
     */
    public void setMaxHistorySize(int maxHistorySize) {
        this.maxHistorySize = maxHistorySize;
    }
    
    /**
     * 获取最大历史消息数量
     */
    public int getMaxHistorySize() {
        return maxHistorySize;
    }
    
    /**
     * 获取对话历史
     */
    public List<Message> getHistory() {
        return new ArrayList<>(history);
    }
    
    /**
     * 清空历史记录
     */
    public void clearHistory() {
        history.clear();
    }
    
    /**
     * 设置系统消息
     * 
     * 系统消息独立存储，始终作为消息列表的第一条消息。
     * 学习要点：System Prompt 的作用和注入时机
     */
    public void setSystemMessage(String content) {
        this.systemMessage = Message.system(content);
    }
    
    /**
     * 设置系统消息（使用 Message 对象）
     */
    public void setSystemMessage(Message message) {
        this.systemMessage = message;
    }
    
    /**
     * 获取系统消息
     */
    public Message getSystemMessage() {
        return systemMessage;
    }
    
    /**
     * 清除系统消息
     */
    public void clearSystemMessage() {
        this.systemMessage = null;
    }
    
    /**
     * 设置上下文窗口管理器
     */
    public void setContextWindowManager(ContextWindowManager manager) {
        this.contextWindowManager = manager;
    }
    
    /**
     * 获取上下文窗口管理器
     */
    public ContextWindowManager getContextWindowManager() {
        return contextWindowManager;
    }
    
    /**
     * 根据模型 ID 更新上下文窗口管理器
     * 
     * 如果模型发生变化（如切换模型），调用此方法更新上下文管理器。
     * 注意：需要传入已配置好的 ContextWindowManager。
     * 
     * @param manager 已配置的上下文窗口管理器
     */
    public void updateContextWindowManager(ContextWindowManager manager) {
        this.contextWindowManager = manager;
    }
    
    /**
     * 获取经过窗口管理的消息列表
     * 
     * 简化版：直接返回历史消息，不做预先裁剪。
     * 原因：
     * 1. Token 估算不如 API 返回的 usage 准确
     * 2. 现代模型上下文窗口很大（1M），超出限制的情况很少
     * 3. 主流 Agent（OpenCode, Claude Code）都是这样做的
     * 
     * 如果接近限制，应该警告用户而不是自动裁剪。
     */
    public List<Message> getManagedHistory() {
        List<Message> allMessages = new ArrayList<>();
        if (systemMessage != null) {
            allMessages.add(systemMessage);
        }
        allMessages.addAll(history);
        
        // 调用上下文窗口管理器进行裁剪（如果配置了）
        if (contextWindowManager != null) {
            return contextWindowManager.manageWindow(allMessages, systemMessage);
        }
        return allMessages;
    }
    
    /**
     * 获取当前的 token 数量估算
     * 
     * 需要设置 TokenCountService 才能使用。
     */
    public int getTokenCount() {
        List<Message> allMessages = new ArrayList<>();
        if (systemMessage != null) {
            allMessages.add(systemMessage);
        }
        allMessages.addAll(history);
        
        // 使用上下文窗口管理器进行估算（如果有）
        if (contextWindowManager != null) {
            return contextWindowManager.getCurrentTokenCount(allMessages);
        }
        return allMessages.size();  // 简单回退：消息数量
    }
    
    /**
     * 设置变量
     */
    public void setVariable(String key, Object value) {
        variables.put(key, value);
    }
    
    /**
     * 获取变量
     */
    public Object getVariable(String key) {
        return variables.get(key);
    }
    
    // === ActiveSkill 支持 ===

    /**
     * 设置当前活跃技能
     */
    public void setActiveSkill(SkillDefinition skill) {
        this.activeSkill = skill;
    }

    /**
     * 获取当前活跃技能
     */
    public SkillDefinition getActiveSkill() {
        return activeSkill;
    }

    /**
     * 检查是否有活跃技能
     */
    public boolean hasActiveSkill() {
        return activeSkill != null;
    }

    /**
     * 清除当前活跃技能
     */
    public void clearActiveSkill() {
        this.activeSkill = null;
    }

    // Getters
    public String getSessionId() { return sessionId; }
    public Path getWorkingDirectory() { return workingDirectory; }
    public void setWorkingDirectory(Path path) { this.workingDirectory = path; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public LocalDateTime getLastActiveAt() { return lastActiveAt; }
}