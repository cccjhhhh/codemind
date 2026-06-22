package com.codemind.agent.recovery;

import com.codemind.agent.statemachine.HandlerResult;
import com.codemind.agent.engine.ExecutionState;
import com.codemind.agent.statemachine.pattern.ReactState;
import com.codemind.agent.statemachine.StateHandler;

import com.codemind.frontend.output.spi.OutputFormatter;
import com.codemind.llm.Message;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class LoopBreakHandler implements StateHandler {

    private static final Logger log = LoggerFactory.getLogger(LoopBreakHandler.class);
    private final OutputFormatter outputFormatter;

    public LoopBreakHandler(OutputFormatter outputFormatter) {
        this.outputFormatter = outputFormatter;
    }

    @Override
    public HandlerResult handle(ExecutionState state) {
        log.warn("[LoopBreak] 循环检测触发");
        state.outputHandler.accept(outputFormatter.formatWarning(
            "检测到重复操作模式，已打断循环"));
        state.recoveryManager.clearRecentToolCalls();
        state.recoveryManager.setLoopCooldown();
        state.sessionContext.addMessage(Message.user(
            "【循环打断】检测到你连续执行了多次相同操作。请立即停止当前方向，"
            + "回顾原始任务目标，尝试完全不同的方法。"));
        return HandlerResult.withoutCount(ReactState.THINK);
    }
}
