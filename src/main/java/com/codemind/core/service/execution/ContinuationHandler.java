package com.codemind.core.service.execution;

import com.codemind.api.llm.Message;
import com.codemind.core.ContinueReason;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class ContinuationHandler implements StateHandler {

    private static final Logger log = LoggerFactory.getLogger(ContinuationHandler.class);

    @Override
    public HandlerResult handle(ExecutionState state) {
        if (state.recoveryManager.recordContinuation()) {
            state.sessionContext.addMessage(Message.user(
                "【续写】输出被截断，请直接从中断处继续，不要道歉或总结。"));
            return HandlerResult.withCount(ContinueReason.THINK);
        }
        // 续写额度耗尽，退回正常判断
        return HandlerResult.withoutCount(ContinueReason.THINK);
    }
}
