package com.codemind.agent.pattern.planexec.handler;

import com.codemind.agent.engine.ExecutionState;
import com.codemind.agent.pattern.planexec.PlanExecutionState;
import com.codemind.agent.pattern.planexec.PlanState;
import com.codemind.agent.pattern.planexec.StepResult;
import com.codemind.agent.pattern.planexec.StepFailureMode;
import com.codemind.agent.statemachine.HandlerResult;
import com.codemind.agent.statemachine.StateHandler;
import com.codemind.session.SessionContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.concurrent.TimeUnit;

/**
 * StepRetryHandler — 单步重试（指数退避 1s）。
 *
 * <p>重置连续失败计数，回到 STEP_EXECUTE 重新执行当前步骤。</p>
 */
public class StepRetryHandler implements StateHandler {

    private static final Logger log = LoggerFactory.getLogger(StepRetryHandler.class);

    @Override
    public HandlerResult handle(ExecutionState state) {
        SessionContext ctx = state.sessionContext;
        PlanExecutionState pes = getPlanState(ctx);
        if (pes == null || pes.getPlan() == null) {
            return HandlerResult.withCount(PlanState.PLAN_FAILED);
        }

        // 重置连续失败计数
        pes.setConsecutiveFailures(0);
        // 加 1s 等待，给系统喘息时间
        try {
            TimeUnit.SECONDS.sleep(1);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
        log.info("步骤重试：重置连续失败计数，重新执行 {}", pes.currentStep() != null ? pes.currentStep().id() : "当前步骤");

        return HandlerResult.withCount(PlanState.STEP_EXECUTE);
    }

    private PlanExecutionState getPlanState(SessionContext ctx) {
        Object v = ctx.getVariable("_planExecutionState");
        return v instanceof PlanExecutionState ? (PlanExecutionState) v : null;
    }
}
