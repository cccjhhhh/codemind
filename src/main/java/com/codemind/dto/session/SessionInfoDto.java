package com.codemind.dto.session;

import java.time.LocalDateTime;

/**
 * 会话信息 DTO
 * 
 * 用于在会话列表中展示会话元数据。
 */
public class SessionInfoDto {
    
    private final String sessionId;
    private final LocalDateTime createdAt;
    private final LocalDateTime lastActiveAt;
    private final int messageCount;
    
    public SessionInfoDto(String sessionId, LocalDateTime createdAt, 
                         LocalDateTime lastActiveAt, int messageCount) {
        this.sessionId = sessionId;
        this.createdAt = createdAt;
        this.lastActiveAt = lastActiveAt;
        this.messageCount = messageCount;
    }
    
    public String getSessionId() {
        return sessionId;
    }
    
    public LocalDateTime getCreatedAt() {
        return createdAt;
    }
    
    public LocalDateTime getLastActiveAt() {
        return lastActiveAt;
    }
    
    public int getMessageCount() {
        return messageCount;
    }
}