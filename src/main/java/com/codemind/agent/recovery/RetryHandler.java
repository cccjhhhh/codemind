package com.codemind.agent.recovery;

import com.codemind.agent.statemachine.HandlerResult;
import com.codemind.agent.engine.ExecutionState;
import com.codemind.agent.statemachine.pattern.ReactState;
import com.codemind.agent.statemachine.TerminalState;
import com.codemind.agent.statemachine.StateHandler;

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
            return HandlerResult.withCount(TerminalState.ERROR);
        }
        return HandlerResult.withoutCount(ReactState.THINK);
    }
}
