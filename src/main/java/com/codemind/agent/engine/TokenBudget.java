package com.codemind.agent.engine;

import com.codemind.session.TokenCountService;
import com.codemind.llm.Message;
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

    /**
     * @return token 使用率 (0.0 ~ 1.0)，基于可用上下文窗口
     */
    public double getUsageRatio(List<Message> messages) {
        int tokens = tokenCountService.estimateTokens(messages);
        int available = getAvailableTokens();
        if (available <= 0) return 1.0;
        return (double) tokens / available;
    }

    /**
     * 紧急摘要触发 — 仅当 token 使用率 > 95% 时触发 L4。
     * 正常 L1-L3 压缩由 ContextCompressionOrchestrator 基于 compactOnRatio 触发。
     * 这是最后的逃生阀，防止上下文溢出。
     */
    public boolean needsCompact(List<Message> messages) {
        return getUsageRatio(messages) > 0.95;
    }
}
