package com.codemind.core.service.execution;

import com.codemind.api.llm.Message;
import com.codemind.core.ContinueReason;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.function.Function;

public class CompactHandler implements StateHandler {

    private static final Logger log = LoggerFactory.getLogger(CompactHandler.class);
    private final Function<ExecutionState, String> compacter;

    public CompactHandler(Function<ExecutionState, String> compacter) {
        this.compacter = compacter;
    }

    @Override
    public HandlerResult handle(ExecutionState state) {
        try {
            String summary = compacter.apply(state);
            if (summary != null && !summary.isEmpty() && summary.length() > 50) {
                state.sessionContext.clearHistory();
                state.sessionContext.addMessage(Message.user("[Compacted]\n\n" + summary));
                state.recoveryManager.setAttemptedCompact(false);
            } else {
                log.warn("RECOVERY_COMPACT: 摘要失败，注入引导消息");
                state.sessionContext.addMessage(Message.user(
                    "【系统提示】上下文压缩失败，请继续当前任务。"));
            }
        } catch (Exception e) {
            log.error("RECOVERY_COMPACT 失败: {}", e.getMessage());
            state.sessionContext.addMessage(Message.user(
                "【系统提示】上下文压缩异常，请继续当前任务。"));
        }
        return HandlerResult.withoutCount(ContinueReason.THINK);
    }
}
