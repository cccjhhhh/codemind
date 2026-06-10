package com.codemind.core;

import com.codemind.api.session.TokenCountService;
import com.codemind.api.llm.Message;
import java.util.List;

public class TokenBudget {

    private final TokenCountService tokenCountService;
    private final int reservedResponseTokens;
    private final double targetRatio;

    public TokenBudget(TokenCountService tcs, int reserved, double ratio) {
        this.tokenCountService = tcs;
        this.reservedResponseTokens = reserved;
        this.targetRatio = ratio;
    }

    public boolean isOverLimit(List<Message> messages) {
        int tokens = tokenCountService.estimateTokens(messages);
        int available = tokenCountService.getAvailableContextTokens(reservedResponseTokens);
        return tokens > available * targetRatio;
    }

    public int getCurrentTokens(List<Message> messages) {
        return tokenCountService.estimateTokens(messages);
    }

    public int getAvailableTokens() {
        return tokenCountService.getAvailableContextTokens(reservedResponseTokens);
    }

    public int getLimit() {
        return (int)(getAvailableTokens() * targetRatio);
    }

    public boolean needsCompact(List<Message> messages) {
        return getCurrentTokens(messages) > getLimit() * 0.9;
    }
}
