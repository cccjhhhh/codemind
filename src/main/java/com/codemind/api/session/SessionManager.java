package com.codemind.api.session;

/**
 * 会话管理器接口
 * 
 * 管理用户会话的创建、获取、保存和关闭。
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
}