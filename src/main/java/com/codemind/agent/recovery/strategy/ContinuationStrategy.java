package com.codemind.agent.recovery.strategy;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * 续写策略。
 *
 * 当 max_tokens 已达最高级仍然截断时，追加续写提示让 LLM 继续。
 * 最多续写 3 次，超限后返回 false。
 */
public class ContinuationStrategy {

    private static final Logger log = LoggerFactory.getLogger(ContinuationStrategy.class);

    private static final int MAX_CONTINUATIONS = 3;

    private int count = 0;

    /**
     * 记录一次续写请求。
     *
     * @return true 仍在额度内，false 已达上限
     */
    public boolean record() {
        count++;
        if (count > MAX_CONTINUATIONS) {
            log.warn("续写次数已达上限 {}", MAX_CONTINUATIONS);
            return false;
        }
        log.info("续写 {}/{}", count, MAX_CONTINUATIONS);
        return true;
    }

    public boolean isExhausted() {
        return count > MAX_CONTINUATIONS;
    }

    public int getCount() {
        return count;
    }

    public void reset() {
        count = 0;
    }
}
