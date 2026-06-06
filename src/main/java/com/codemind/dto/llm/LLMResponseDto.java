package com.codemind.dto.llm;

import java.util.List;

/**
 * LLM 响应
 */
public class LLMResponseDto {
    
    private final String content;
    private final List<ToolCallDto> toolCalls;
    private final boolean finished;
    private final int tokensUsed;
    
    public LLMResponseDto(String content, List<ToolCallDto> toolCalls, boolean finished, int tokensUsed) {
        this.content = content;
        this.toolCalls = toolCalls;
        this.finished = finished;
        this.tokensUsed = tokensUsed;
    }
    
    public boolean hasToolCalls() {
        return toolCalls != null && !toolCalls.isEmpty();
    }
    
    // Getters
    public String getContent() { return content; }
    public List<ToolCallDto> getToolCalls() { return toolCalls; }
    public boolean isFinished() { return finished; }
    public int getTokensUsed() { return tokensUsed; }
}
