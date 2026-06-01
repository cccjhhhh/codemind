package com.codemind.api.llm;

import java.util.List;

/**
 * LLM 响应
 */
public class LLMResponse {
    
    private final String content;
    private final List<ToolCall> toolCalls;
    private final boolean finished;
    private final int tokensUsed;
    
    public LLMResponse(String content, List<ToolCall> toolCalls, boolean finished, int tokensUsed) {
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
    public List<ToolCall> getToolCalls() { return toolCalls; }
    public boolean isFinished() { return finished; }
    public int getTokensUsed() { return tokensUsed; }
}
