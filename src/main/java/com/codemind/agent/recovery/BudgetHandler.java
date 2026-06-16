package com.codemind.agent.recovery;

import com.codemind.agent.statemachine.HandlerResult;
import com.codemind.agent.engine.ExecutionState;
import com.codemind.agent.statemachine.pattern.ReactState;
import com.codemind.agent.statemachine.StateHandler;

import com.codemind.frontend.output.spi.OutputFormatter;
import com.codemind.llm.Message;

public class BudgetHandler implements StateHandler {

    private final OutputFormatter outputFormatter;

    public BudgetHandler(OutputFormatter outputFormatter) {
        this.outputFormatter = outputFormatter;
    }

    @Override
    public HandlerResult handle(ExecutionState state) {
        state.outputHandler.accept(outputFormatter.formatWarning(
            "Token 预算紧张，请控制回复长度"));
        state.sessionContext.addMessage(Message.user(
            "【Token 预算警告】上下文空间不足，请尽量精简回复，省略无关细节。"));
        return HandlerResult.withCount(ReactState.THINK);
    }
}
