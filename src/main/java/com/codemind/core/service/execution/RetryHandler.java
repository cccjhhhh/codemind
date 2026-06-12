package com.codemind.core.service.execution;

import com.codemind.core.ContinueReason;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class RetryHandler implements StateHandler {

    private static final Logger log = LoggerFactory.getLogger(RetryHandler.class);

    @Override
    public HandlerResult handle(ExecutionState state) {
        long delay = Math.min(
            1000L * (1L << state.recoveryManager.getConsecutive529()), 32000L);
        log.info("瞬态错误退避 {}ms", delay);
        try {
            Thread.sleep(delay);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return HandlerResult.withCount(ContinueReason.ERROR);
        }
        return HandlerResult.withoutCount(ContinueReason.THINK);
    }
}
