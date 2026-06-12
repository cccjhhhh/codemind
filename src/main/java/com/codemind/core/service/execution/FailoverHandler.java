package com.codemind.core.service.execution;

import com.codemind.api.cli.OutputFormatter;
import com.codemind.core.ContinueReason;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class FailoverHandler implements StateHandler {

    private static final Logger log = LoggerFactory.getLogger(FailoverHandler.class);
    private final OutputFormatter outputFormatter;

    public FailoverHandler(OutputFormatter outputFormatter) {
        this.outputFormatter = outputFormatter;
    }

    @Override
    public HandlerResult handle(ExecutionState state) {
        state.recoveryManager.switchToFallback();
        state.outputHandler.accept(outputFormatter.formatWarning(
            "连续失败，切换 fallback 模型: " + state.recoveryManager.isUsingFallback()));
        state.sessionContext.setVariable("_consecutiveFailures", 0);
        return HandlerResult.withCount(ContinueReason.THINK);
    }
}
