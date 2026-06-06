package com.codemind.dto.session;

import java.nio.file.Path;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

/**
 * 会话上下文数据传输对象
 */
public class SessionContextDto {
    
    private final String sessionId;
    private Path workingDirectory;
    private LocalDateTime createdAt;
    private LocalDateTime lastActiveAt;
    private int maxHistorySize;
    
    public SessionContextDto(String sessionId) {
        this.sessionId = sessionId;
        this.workingDirectory = Path.of(System.getProperty("user.dir"));
        this.createdAt = LocalDateTime.now();
        this.lastActiveAt = LocalDateTime.now();
        this.maxHistorySize = 100;
    }
    
    public String getSessionId() { return sessionId; }
    public Path getWorkingDirectory() { return workingDirectory; }
    public void setWorkingDirectory(Path path) { this.workingDirectory = path; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public LocalDateTime getLastActiveAt() { return lastActiveAt; }
    public void setLastActiveAt(LocalDateTime time) { this.lastActiveAt = time; }
    public int getMaxHistorySize() { return maxHistorySize; }
    public void setMaxHistorySize(int size) { this.maxHistorySize = size; }
}
