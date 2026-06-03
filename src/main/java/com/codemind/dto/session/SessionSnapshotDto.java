package com.codemind.dto.session;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 会话快照 DTO
 * 
 * 用于 JSON 序列化/反序列化会话状态。
 */
public class SessionSnapshotDto {
    
    @JsonProperty("session_id")
    private String sessionId;
    
    @JsonProperty("working_directory")
    private String workingDirectory;
    
    private List<MessageDto> history;
    
    @JsonProperty("system_message")
    private String systemMessage;
    
    @JsonProperty("created_at")
    private LocalDateTime createdAt;
    
    @JsonProperty("last_active_at")
    private LocalDateTime lastActiveAt;
    
    // 空的构造函数用于反序列化
    public SessionSnapshotDto() {}
    
    public SessionSnapshotDto(String sessionId, String workingDirectory,
                              LocalDateTime createdAt, LocalDateTime lastActiveAt,
                              List<MessageDto> history, String systemMessage) {
        this.sessionId = sessionId;
        this.workingDirectory = workingDirectory;
        this.createdAt = createdAt;
        this.lastActiveAt = lastActiveAt;
        this.history = history;
        this.systemMessage = systemMessage;
    }
    
    public String getSessionId() {
        return sessionId;
    }
    
    public String getWorkingDirectory() {
        return workingDirectory;
    }
    
    public List<MessageDto> getHistory() {
        return history;
    }
    
    public String getSystemMessage() {
        return systemMessage;
    }
    
    public LocalDateTime getCreatedAt() {
        return createdAt;
    }
    
    public LocalDateTime getLastActiveAt() {
        return lastActiveAt;
    }
    
    // Getters 和 Setters 用于 Jackson 反序列化
    public void setSessionId(String sessionId) {
        this.sessionId = sessionId;
    }
    
    public void setWorkingDirectory(String workingDirectory) {
        this.workingDirectory = workingDirectory;
    }
    
    public void setHistory(List<MessageDto> history) {
        this.history = history;
    }
    
    public void setSystemMessage(String systemMessage) {
        this.systemMessage = systemMessage;
    }
    
    public void setCreatedAt(LocalDateTime createdAt) {
        this.createdAt = createdAt;
    }
    
    public void setLastActiveAt(LocalDateTime lastActiveAt) {
        this.lastActiveAt = lastActiveAt;
    }
}