package com.codemind.core;

import com.codemind.api.llm.LLMResponse;

public class StopHook {

    public StopHook() {}

    public ContinueReason evaluate(LLMResponse response, boolean hasToolCalls) {
        if (hasToolCalls) {
            return null;  // 有工具调用，继续
        }
        if (response == null || response.getContent() == null || response.getContent().isBlank()) {
            return ContinueReason.ERROR;
        }
        return ContinueReason.COMPLETE;
    }
}
