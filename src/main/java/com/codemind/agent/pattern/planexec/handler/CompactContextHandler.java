package com.codemind.agent.pattern.planexec.handler;

import com.codemind.agent.engine.ExecutionState;
import com.codemind.agent.pattern.planexec.PlanExecutionState;
import com.codemind.agent.pattern.planexec.PlanState;
import com.codemind.agent.statemachine.HandlerResult;
import com.codemind.agent.statemachine.StateHandler;
import com.codemind.session.SessionContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.function.Function;

/**
 * CompactContextHandler — 上下文压缩后重试。
 *
 * <p>当执行过程中遇到上下文溢出时，触发 Plan-aware 压缩，
 * 然后回到 STEP_EXECUTE 继续执行。</p>
 */
public class CompactContextHandler implements StateHandler {

    private static final Logger log = LoggerFactory.getLogger(CompactContextHandler.class);
    private final Function<ExecutionState, String> compacter;

    public CompactContextHandler(Function<ExecutionState, String> compacter) {
        this.compacter = compacter;
    }

    @Override
    public HandlerResult handle(ExecutionState state) {
        try {
            String summary = compacter.apply(state);
            if (summary != null && !summary.isBlank()) {
                log.info("Plan-aware 上下文压缩完成，摘要长度: {}", summary.length());
            }
        } catch (Exception e) {
            log.warn("上下文压缩失败: {}", e.getMessage());
        }
        return HandlerResult.withCount(PlanState.STEP_EXECUTE);
    }
}
