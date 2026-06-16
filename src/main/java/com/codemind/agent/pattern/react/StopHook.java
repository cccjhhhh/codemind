package com.codemind.agent.pattern.react;

import com.codemind.agent.statemachine.TerminalState;
import com.codemind.llm.LLMResponse;

public class StopHook {

    public StopHook() {}

    public TerminalState evaluate(LLMResponse response, boolean hasToolCalls) {
        if (hasToolCalls) {
            return null;  // 有工具调用，继续
        }
        if (response == null || response.getContent() == null || response.getContent().isBlank()) {
            return TerminalState.ERROR;
        }
        return TerminalState.COMPLETE;
    }
}
