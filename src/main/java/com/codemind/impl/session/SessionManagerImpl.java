package com.codemind.impl.session;

import com.codemind.api.session.SessionContext;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 会话管理器实现
 * 
 * 管理用户会话的创建、获取、保存和关闭。
 * 学习要点：会话生命周期管理、多用户隔离
 */
public class SessionManagerImpl implements com.codemind.api.session.SessionManager {
    
    private final Map<String, SessionContext> sessions = new ConcurrentHashMap<>();
    
    @Override
    public SessionContext createSession() {
        String sessionId = UUID.randomUUID().toString();
        SessionContext context = new SessionContext(sessionId);
        sessions.put(sessionId, context);
        return context;
    }
    
    @Override
    public SessionContext getSession(String sessionId) {
        return sessions.get(sessionId);
    }
    
    @Override
    public SessionContext getOrCreateSession(String sessionId) {
        return sessions.computeIfAbsent(sessionId, id -> new SessionContext(id));
    }
    
    @Override
    public void closeSession(String sessionId) {
        sessions.remove(sessionId);
    }
    
    @Override
    public int getActiveSessionCount() {
        return sessions.size();
    }
    
    @Override
    public void clearAllSessions() {
        sessions.clear();
    }
}