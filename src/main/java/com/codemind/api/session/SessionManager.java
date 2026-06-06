package com.codemind.api.session;

/**
 * 会话管理器接口
 * 
 * 管理用户会话的创建、获取、保存和关闭。
 * 学习要点：会话生命周期管理、多用户隔离
 */
public interface SessionManager {
    
    /**
     * 创建新会话
     */
    SessionContext createSession();
    
    /**
     * 获取会话
     */
    SessionContext getSession(String sessionId);
    
    /**
     * 获取或创建会话（如果不存在则创建）
     */
    SessionContext getOrCreateSession(String sessionId);
    
    /**
     * 关闭会话
     */
    void closeSession(String sessionId);
    
    /**
     * 保存会话到持久化存储
     * 
     * @param sessionId 会话 ID
     */
    void saveSession(String sessionId);
    
    /**
     * 获取活跃会话数量
     */
    int getActiveSessionCount();
    
    /**
     * 清理所有会话
     */
    void clearAllSessions();
}