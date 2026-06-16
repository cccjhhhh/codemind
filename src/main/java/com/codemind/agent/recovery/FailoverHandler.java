package com.codemind.agent.recovery;

import com.codemind.agent.statemachine.HandlerResult;
import com.codemind.agent.engine.ExecutionState;
import com.codemind.agent.statemachine.pattern.ReactState;
import com.codemind.agent.statemachine.StateHandler;

import com.codemind.frontend.output.spi.OutputFormatter;
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
        return HandlerResult.withCount(ReactState.THINK);
    }
}
