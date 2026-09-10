package com.codemind.agent.pattern.planexec.handler;

import com.codemind.agent.engine.ExecutionState;
import com.codemind.agent.pattern.planexec.PlanExecutionState;
import com.codemind.agent.pattern.planexec.PlanState;
import com.codemind.agent.pattern.planexec.PlanStep;
import com.codemind.agent.pattern.planexec.StepResult;
import com.codemind.agent.statemachine.HandlerResult;
import com.codemind.agent.statemachine.StateHandler;
import com.codemind.frontend.output.spi.OutputFormatter;
import com.codemind.session.SessionContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * StepSkipHandler — 处理"依赖失败导致跳过"的步骤。
 *
 * <p>语义：当前步骤的前置步骤失败，本步骤无法执行，直接跳过并记录到 failedSteps。</p>
 *
 * <p>流转：记录跳过 → 推进到下一步 → STEP_EXECUTE</p>
 */
public class StepSkipHandler implements StateHandler {

    private static final Logger log = LoggerFactory.getLogger(StepSkipHandler.class);

    private final OutputFormatter outputFormatter;

    public StepSkipHandler(OutputFormatter outputFormatter) {
        this.outputFormatter = outputFormatter;
    }

    @Override
    public HandlerResult handle(ExecutionState state) {
        SessionContext ctx = state.sessionContext;
        PlanExecutionState pes = getPlanState(ctx);
        if (pes == null || pes.getPlan() == null) {
            return HandlerResult.withCount(PlanState.PLAN_FAILED);
        }

        PlanStep step = pes.currentStep();
        if (step == null) {
            log.info("所有步骤已执行/跳过完成");
            return HandlerResult.withCount(PlanState.PLAN_COMPLETE);
        }

        // 记录跳过
        StepResult skipped = StepResult.skipped(step.id(), "依赖失败跳过");
        pes.addFailedStep(skipped);
        state.outputHandler.accept(outputFormatter.formatStepSkipped(step.id(), "dependency-failed"));
        log.info("步骤 {} 跳过（依赖失败）", step.id());

        // 推进到下一步
        pes.advance();
        return HandlerResult.withCount(PlanState.STEP_EXECUTE);
    }

    private PlanExecutionState getPlanState(SessionContext ctx) {
        Object v = ctx.getVariable("_planExecutionState");
        return v instanceof PlanExecutionState ? (PlanExecutionState) v : null;
    }
}
