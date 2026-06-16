package com.codemind.agent.recovery;

import com.codemind.agent.statemachine.HandlerResult;
import com.codemind.agent.engine.ExecutionState;
import com.codemind.agent.statemachine.pattern.ReactState;
import com.codemind.agent.statemachine.StateHandler;

import com.codemind.agent.recovery.RecoveryManager;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class EscalateHandler implements StateHandler {

    private static final Logger log = LoggerFactory.getLogger(EscalateHandler.class);

    @Override
    public HandlerResult handle(ExecutionState state) {
        RecoveryManager rm = state.recoveryManager;
        log.info("RECOVERY_ESCALATE: max_tokens -> {} (stage {})",
            rm.getCurrentMaxTokens(), rm.getMaxTokensStage());
        return HandlerResult.withCount(ReactState.THINK);
    }
}
