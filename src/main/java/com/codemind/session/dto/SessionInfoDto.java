package com.codemind.session.dto;

import java.time.LocalDateTime;

public class SessionInfoDto {

    private String sessionId;
    private LocalDateTime createdAt;
    private LocalDateTime lastActiveAt;
    private int historySize;

    public SessionInfoDto() {}

    public SessionInfoDto(String sessionId, LocalDateTime createdAt, LocalDateTime lastActiveAt, int historySize) {
        this.sessionId = sessionId;
        this.createdAt = createdAt;
        this.lastActiveAt = lastActiveAt;
        this.historySize = historySize;
    }

    // Getters and setters for Jackson

    public String getSessionId() { return sessionId; }
    public void setSessionId(String sessionId) { this.sessionId = sessionId; }

    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }

    public LocalDateTime getLastActiveAt() { return lastActiveAt; }
    public void setLastActiveAt(LocalDateTime lastActiveAt) { this.lastActiveAt = lastActiveAt; }

    public int getHistorySize() { return historySize; }
    public void setHistorySize(int historySize) { this.historySize = historySize; }
}
