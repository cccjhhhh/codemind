package com.codemind.agent.pattern.planexec.handler;

import com.codemind.agent.engine.ExecutionState;
import com.codemind.agent.pattern.planexec.PlanExecutionState;
import com.codemind.agent.pattern.planexec.PlanState;
import com.codemind.agent.statemachine.HandlerResult;
import com.codemind.agent.statemachine.StateHandler;
import com.codemind.frontend.output.spi.OutputFormatter;
import com.codemind.session.SessionContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Map;
import java.util.stream.Collectors;

/**
 * PlanPartialHandler — 处理部分成功的计划。
 *
 * <p>语义：重规划失败后，返回已完成步骤的摘要，标记为部分成功。</p>
 *
 * <p>关键：返回 success 路径（由 WorkflowOrchestrator 处理），而非 failure。</p>
 *
 * <p>流转：输出摘要 → PLAN_PARTIAL（终止态）</p>
 */
public class PlanPartialHandler implements StateHandler {

    private static final Logger log = LoggerFactory.getLogger(PlanPartialHandler.class);

    private final OutputFormatter outputFormatter;
    private final Map<String, String> fileContentCache;

    public PlanPartialHandler(OutputFormatter outputFormatter,
                              Map<String, String> fileContentCache) {
        this.outputFormatter = outputFormatter;
        this.fileContentCache = fileContentCache;
    }

    @Override
    public HandlerResult handle(ExecutionState state) {
        SessionContext ctx = state.sessionContext;
        PlanExecutionState pes = getPlanState(ctx);
        if (pes == null || pes.getPlan() == null) {
            return HandlerResult.withoutCount(PlanState.PLAN_FAILED);
        }

        // 构建已完成步骤摘要
        String summary = buildSummary(pes);
        state.outputHandler.accept(outputFormatter.formatWarning(summary));

        log.info("计划部分成功: completed={}, failed={}",
            pes.getCompletedCount(), pes.getFailedCount());

        // PLAN_PARTIAL 作为终止态，由 WorkflowOrchestrator 判断走 success 还是 failure
        return HandlerResult.withoutCount(PlanState.PLAN_PARTIAL);
    }

    private String buildSummary(PlanExecutionState pes) {
        StringBuilder sb = new StringBuilder();
        sb.append("[Plan Partial Success]\n");
        sb.append(String.format("Completed: %d/%d steps\n",
            pes.getCompletedCount(),
            pes.getPlan().steps().size()));

        // 收集已完成步骤的输出摘要
        String completedOutput = pes.getCompletedSteps().stream()
            .map(r -> String.format("  [%s] %s", r.stepId(),
                r.actualOutput() != null
                    ? (r.actualOutput().length() > 200
                        ? r.actualOutput().substring(0, 200) + "..."
                        : r.actualOutput())
                    : "(no output)"))
            .collect(Collectors.joining("\n"));
        if (!completedOutput.isEmpty()) {
            sb.append("\nCompleted steps output:\n").append(completedOutput);
        }

        // 注入缓存的文件内容
        if (!fileContentCache.isEmpty()) {
            sb.append("\n\nCached file contents:\n");
            fileContentCache.forEach((path, content) ->
                sb.append(String.format("  [%s] %s chars\n", path, content.length())));
        }

        return sb.toString();
    }

    private PlanExecutionState getPlanState(SessionContext ctx) {
        Object v = ctx.getVariable("_planExecutionState");
        return v instanceof PlanExecutionState ? (PlanExecutionState) v : null;
    }
}
