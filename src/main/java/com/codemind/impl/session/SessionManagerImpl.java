package com.codemind.impl.session;

import com.codemind.api.llm.Message;
import com.codemind.api.session.SessionContext;
import com.codemind.api.session.SessionManager;
import com.codemind.dto.session.SessionInfoDto;
import com.codemind.dto.session.SessionMessageDto;
import com.codemind.dto.session.SessionSnapshotDto;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;

import java.io.File;
import java.io.IOException;
import java.nio.file.Paths;
import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 会话管理器实现
 * 
 * 管理用户会话的创建、获取、保存和关闭。
 * 支持会话持久化到 JSON 文件。
 * 
 */
public class SessionManagerImpl implements SessionManager {
    
    private static final String SESSION_DIR = System.getProperty("user.home") + "/.codemind/sessions";
    
    private final Map<String, SessionContext> sessions = new ConcurrentHashMap<>();
    private final ObjectMapper objectMapper;
    private final File sessionDir;
    private final SlidingWindowContextManager defaultContextManager;
    
    public SessionManagerImpl() {
        this(new SlidingWindowContextManager());
    }
    
    /**
     * 依赖注入构造器
     * 
     * @param contextManager 默认的上下文窗口管理器
     */
    public SessionManagerImpl(SlidingWindowContextManager contextManager) {
        this.objectMapper = new ObjectMapper();
        this.objectMapper.registerModule(new JavaTimeModule());
        this.objectMapper.enable(SerializationFeature.INDENT_OUTPUT);
        
        // 确保会话目录存在
        this.sessionDir = new File(SESSION_DIR);
        if (!sessionDir.exists()) {
            sessionDir.mkdirs();
        }
        
        this.defaultContextManager = Objects.requireNonNull(contextManager, "contextManager cannot be null");
    }
    
    @Override
    public SessionContext createSession() {
        String sessionId = UUID.randomUUID().toString();
        SessionContext context = new SessionContext(sessionId);
        
        // 使用注入的上下文窗口管理器
        context.setContextWindowManager(defaultContextManager);
        
        sessions.put(sessionId, context);
        return context;
    }
    
    @Override
    public SessionContext getSession(String sessionId) {
        return sessions.get(sessionId);
    }
    
    @Override
    public SessionContext getOrCreateSession(String sessionId) {
        SessionContext context = sessions.computeIfAbsent(sessionId, id -> {
            SessionContext newContext = new SessionContext(id);
            newContext.setContextWindowManager(defaultContextManager);
            return newContext;
        });
        return context;
    }
    
    @Override
    public void closeSession(String sessionId) {
        // 先保存会话
        saveSession(sessionId);
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
    
    /**
     * 保存会话到文件
     * 
     * 会话以 JSON 格式保存到 ~/.codemind/sessions/{sessionId}.json
     */
    public void saveSession(String sessionId) {
        SessionContext context = sessions.get(sessionId);
        if (context == null) {
            return;
        }
        
        try {
            SessionSnapshotDto snapshot = createSnapshot(context);
            File file = getSessionFile(sessionId);
            objectMapper.writeValue(file, snapshot);
        } catch (IOException e) {
            System.err.println("保存会话失败: " + e.getMessage());
        }
    }
    
    /**
     * 从文件加载会话
     * 
     * @param sessionId 会话 ID
     * @return 加载的会话上下文，如果不存在则返回 null
     */
    public SessionContext loadSession(String sessionId) {
        File file = getSessionFile(sessionId);
        if (!file.exists()) {
            return null;
        }
        
        try {
            SessionSnapshotDto snapshot = objectMapper.readValue(file, SessionSnapshotDto.class);
            SessionContext context = restoreFromSnapshot(snapshot);
            
            // 设置上下文窗口管理器
            context.setContextWindowManager(defaultContextManager);
            
            sessions.put(sessionId, context);
            return context;
        } catch (IOException e) {
            System.err.println("加载会话失败: " + e.getMessage());
            return null;
        }
    }
    
    /**
     * 获取会话文件
     */
    private File getSessionFile(String sessionId) {
        return new File(sessionDir, sessionId + ".json");
    }
    
    /**
     * 保存所有活跃会话
     * 
     * 预留方法 - 供未来多会话场景使用
     * 
     * 保留理由：
     * 1. 未来可能支持多会话并发运行（如多个终端窗口）
     * 2. JVM 关闭钩子可调用此方法保存所有会话
     * 3. 定时自动保存功能需要此方法
     */
    public void saveAllSessions() {
        for (String sessionId : sessions.keySet()) {
            saveSession(sessionId);
        }
    }
    
    /**
     * 列出所有已保存的会话文件
     */
    public List<SessionInfoDto> listSavedSessions() {
        List<SessionInfoDto> result = new ArrayList<>();
        File[] files = sessionDir.listFiles((dir, name) -> name.endsWith(".json"));
        
        if (files != null) {
            for (File file : files) {
                try {
                    SessionSnapshotDto snapshot = objectMapper.readValue(file, SessionSnapshotDto.class);
                    result.add(new SessionInfoDto(
                        snapshot.getSessionId(),
                        snapshot.getCreatedAt(),
                        snapshot.getLastActiveAt(),
                        snapshot.getHistory() != null ? snapshot.getHistory().size() : 0
                    ));
                } catch (IOException e) {
                    // 忽略无效的会话文件
                }
            }
        }
        
        return result;
    }
    
    /**
     * 创建会话快照
     */
    private SessionSnapshotDto createSnapshot(SessionContext context) {
        String sessionId = context.getSessionId();
        String workingDirectory = context.getWorkingDirectory().toString();
        LocalDateTime createdAt = context.getCreatedAt();
        LocalDateTime lastActiveAt = context.getLastActiveAt();
        
        // 转换历史消息
        List<SessionMessageDto> history = new ArrayList<>();
        for (Message msg : context.getHistory()) {
            history.add(SessionMessageDto.fromMessage(msg));
        }
        
        // 提取系统消息内容
        String systemMessage = null;
        if (context.getSystemMessage() != null) {
            systemMessage = context.getSystemMessage().getContent();
        }
        
        return new SessionSnapshotDto(sessionId, workingDirectory, createdAt, lastActiveAt, history, systemMessage);
    }
    
    /**
     * 从快照恢复会话
     */
    private SessionContext restoreFromSnapshot(SessionSnapshotDto snapshot) {
        SessionContext context = new SessionContext(snapshot.getSessionId());
        
        // 恢复工作目录
        context.setWorkingDirectory(Paths.get(snapshot.getWorkingDirectory()));
        
        // 恢复系统消息
        if (snapshot.getSystemMessage() != null && !snapshot.getSystemMessage().isEmpty()) {
            context.setSystemMessage(snapshot.getSystemMessage());
        }
        
        // 恢复历史消息
        if (snapshot.getHistory() != null) {
            for (SessionMessageDto dto : snapshot.getHistory()) {
                context.addMessage(dto.toMessage());
            }
        }
        
        return context;
    }
}