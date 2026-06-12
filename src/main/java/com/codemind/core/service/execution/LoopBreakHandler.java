package com.codemind.core.service.execution;

import com.codemind.api.cli.OutputFormatter;
import com.codemind.api.llm.Message;
import com.codemind.core.ContinueReason;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.function.Function;

public class LoopBreakHandler implements StateHandler {

    private static final Logger log = LoggerFactory.getLogger(LoopBreakHandler.class);
    private final OutputFormatter outputFormatter;
    private final Function<ExecutionState, String> compacter;

    public LoopBreakHandler(OutputFormatter outputFormatter, Function<ExecutionState, String> compacter) {
        this.outputFormatter = outputFormatter;
        this.compacter = compacter;
    }

    @Override
    public HandlerResult handle(ExecutionState state) {
        log.warn("[LoopBreak] 循环检测触发，开始压缩上下文");
        state.outputHandler.accept(outputFormatter.formatWarning(
            "检测到重复操作模式，触发上下文压缩"));
        try {
            state.recoveryManager.clearRecentToolCalls();
            state.recoveryManager.setLoopCooldown();
            String summary = compacter.apply(state);

            if (summary != null && !summary.isEmpty() && summary.length() > 50) {
                log.info("[LoopBreak] 压缩摘要长度: {} 字符", summary.length());
                state.sessionContext.clearHistory();
                state.sessionContext.addMessage(Message.user(
                    "[Compacted — loop break]\n\n" + summary));
                state.recoveryManager.setAttemptedCompact(false);
            } else {
                log.warn("[LoopBreak] 压缩摘要失败，注入引导消息");
                state.sessionContext.addMessage(Message.user(
                    "【系统提示】检测到你陷入重复操作。请立即停止当前操作，"
                    + "尝试完全不同的方法或等待用户指示。"));
            }
        } catch (Exception e) {
            log.error("LOOP_DETECTED 压缩失败: {}", e.getMessage());
            state.sessionContext.addMessage(Message.user(
                "【系统提示】检测到循环操作，请尝试不同的方法。"));
        }
        return HandlerResult.withoutCount(ContinueReason.THINK);
    }
}
