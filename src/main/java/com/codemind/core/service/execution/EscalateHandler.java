package com.codemind.core.service.execution;

import com.codemind.core.ContinueReason;
import com.codemind.core.RecoveryManager;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class EscalateHandler implements StateHandler {

    private static final Logger log = LoggerFactory.getLogger(EscalateHandler.class);

    @Override
    public HandlerResult handle(ExecutionState state) {
        RecoveryManager rm = state.recoveryManager;
        log.info("RECOVERY_ESCALATE: max_tokens -> {} (stage {})",
            rm.getCurrentMaxTokens(), rm.getMaxTokensStage());
        return HandlerResult.withCount(ContinueReason.THINK);
    }
}
