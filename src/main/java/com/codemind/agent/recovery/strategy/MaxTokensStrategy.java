package com.codemind.agent.recovery.strategy;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * max_tokens 升级策略。
 *
 * 三级升级：8192 → 32768 → 65536
 * 每次 max_tokens 截断触发升级，升级后重新请求 LLM。
 * 已达最高级则返回 false，由调用方决定是否走续写路径。
 */
public class MaxTokensStrategy {

    private static final Logger log = LoggerFactory.getLogger(MaxTokensStrategy.class);

    private static final int[] MAX_TOKENS_STAGES = {8192, 32768, 65536};
    private static final int DEFAULT_MAX_TOKENS = 8192;

    private int stage = 0;
    private int currentMaxTokens = DEFAULT_MAX_TOKENS;

    /**
     * 尝试升级 max_tokens 到下一级。
     *
     * @return true 升级成功，false 已达最高级
     */
    public boolean escalate() {
        if (stage >= MAX_TOKENS_STAGES.length - 1) {
            log.warn("max_tokens 已达最高级 {}", currentMaxTokens);
            return false;
        }
        stage++;
        currentMaxTokens = MAX_TOKENS_STAGES[stage];
        log.info("max_tokens 升级: stage={}, value={}", stage, currentMaxTokens);
        return true;
    }

    public int getCurrentMaxTokens() {
        return currentMaxTokens;
    }

    public int getStage() {
        return stage;
    }

    public boolean isExhausted() {
        return stage >= MAX_TOKENS_STAGES.length - 1;
    }

    public void reset() {
        stage = 0;
        currentMaxTokens = DEFAULT_MAX_TOKENS;
    }
}
