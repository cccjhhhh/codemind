package com.codemind.api.session;

import com.codemind.api.llm.Message;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 会话上下文
 * 
 * 存储当前会话的状态信息，包括：
 * - 工作目录
 * - 对话历史
 * - 变量存储
 * 
 * 学习要点：上下文管理、状态持久化
 */
public class SessionContext {
    
    private final String sessionId;
    private Path workingDirectory;
    private final List<Message> history;
    private final Map<String, Object> variables;
    private LocalDateTime createdAt;
    private LocalDateTime lastActiveAt;
    
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
     */
    public void addMessage(Message message) {
        history.add(message);
        lastActiveAt = LocalDateTime.now();
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
    
    // Getters
    public String getSessionId() { return sessionId; }
    public Path getWorkingDirectory() { return workingDirectory; }
    public void setWorkingDirectory(Path path) { this.workingDirectory = path; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public LocalDateTime getLastActiveAt() { return lastActiveAt; }
}