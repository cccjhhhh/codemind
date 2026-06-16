package com.codemind.session.dto;

import java.time.LocalDateTime;
import java.util.List;

public class SessionSnapshotDto {

    private String sessionId;
    private String workingDirectory;
    private LocalDateTime createdAt;
    private LocalDateTime lastActiveAt;
    private List<SessionMessageDto> history;
    private String systemMessage;

    public SessionSnapshotDto() {}

    public SessionSnapshotDto(String sessionId, String workingDirectory,
                              LocalDateTime createdAt, LocalDateTime lastActiveAt,
                              List<SessionMessageDto> history, String systemMessage) {
        this.sessionId = sessionId;
        this.workingDirectory = workingDirectory;
        this.createdAt = createdAt;
        this.lastActiveAt = lastActiveAt;
        this.history = history;
        this.systemMessage = systemMessage;
    }

    // Getters and setters for Jackson

    public String getSessionId() { return sessionId; }
    public void setSessionId(String sessionId) { this.sessionId = sessionId; }

    public String getWorkingDirectory() { return workingDirectory; }
    public void setWorkingDirectory(String workingDirectory) { this.workingDirectory = workingDirectory; }

    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }

    public LocalDateTime getLastActiveAt() { return lastActiveAt; }
    public void setLastActiveAt(LocalDateTime lastActiveAt) { this.lastActiveAt = lastActiveAt; }

    public List<SessionMessageDto> getHistory() { return history; }
    public void setHistory(List<SessionMessageDto> history) { this.history = history; }

    public String getSystemMessage() { return systemMessage; }
    public void setSystemMessage(String systemMessage) { this.systemMessage = systemMessage; }
}
