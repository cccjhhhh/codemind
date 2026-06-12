package com.codemind.core.service.execution;

import com.codemind.api.cli.OutputFormatter;
import com.codemind.api.llm.Message;
import com.codemind.core.ContinueReason;

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
        return HandlerResult.withCount(ContinueReason.THINK);
    }
}
