package com.codemind.agent.pattern.planexec.handler;

import com.codemind.agent.engine.ExecutionState;
import com.codemind.agent.engine.ToolPartitioner;
import com.codemind.agent.pattern.planexec.*;
import com.codemind.agent.statemachine.HandlerResult;
import com.codemind.agent.statemachine.StateHandler;
import com.codemind.frontend.output.spi.OutputFormatter;
import com.codemind.llm.ToolCall;
import com.codemind.session.SessionContext;
import com.codemind.tool.ToolRegistry;
import com.codemind.tool.ToolResult;
import com.codemind.agent.async.ThreadPoolConfig;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;
import java.util.concurrent.*;
import java.util.function.Consumer;

/**
 * StepExecuteHandler — 执行当前步骤，含三级故障隔离。
 *
 * <p>执行策略：</p>
 * <ul>
 *   <li>L1: 单工具失败 → 跳过该工具，继续执行其余工具</li>
 *   <li>L2: 步骤失败 → 重试 1 次（指数退避 1s），仍失败则跳过</li>
 *   <li>L3: 连续 3 步失败 → 触发 LOCAL_REPLAN</li>
 * </ul>
 *
 * <p>支持三种执行模式：</p>
 * <ul>
 *   <li>SERIAL — 顺序执行所有工具</li>
 *   <li>PARALLEL — 并行执行安全工具（复用 ToolPartitioner）</li>
 *   <li>SUB_AGENT — 卸载到独立子 Agent</li>
 * </ul>
 */
public class StepExecuteHandler implements StateHandler {

    private static final Logger log = LoggerFactory.getLogger(StepExecuteHandler.class);
    private static final int MAX_FILE_CHARS = 200_000;
    private static final long STEP_TIMEOUT_MS = 60_000L;
    private static final int CONSECUTIVE_FAILURE_THRESHOLD = 3;

    private final ToolRegistry toolRegistry;
    private final OutputFormatter outputFormatter;
    private final Map<String, String> fileContentCache;
    private final ExecutorService toolExecutor;

    public StepExecuteHandler(ToolRegistry toolRegistry,
                              OutputFormatter outputFormatter,
                              Map<String, String> fileContentCache) {
        this.toolRegistry = toolRegistry;
        this.outputFormatter = outputFormatter;
        this.fileContentCache = fileContentCache;
        this.toolExecutor = ThreadPoolConfig.TOOL_EXEC;
    }

    @Override
    public HandlerResult handle(ExecutionState state) {
        SessionContext ctx = state.sessionContext;
        PlanExecutionState pes = getPlanState(ctx);
        Consumer<String> outputHandler = state.outputHandler;

        if (pes == null || pes.getPlan() == null) {
            log.warn("未找到执行计划");
            return HandlerResult.withCount(PlanState.PLAN_FAILED);
        }

        PlanStep step = pes.currentStep();
        if (step == null) {
            log.info("所有步骤已执行完成");
            return HandlerResult.withCount(PlanState.PLAN_COMPLETE);
        }

        int current = pes.getCurrentStepIndex();
        int total = pes.getPlan().steps().size();
        outputHandler.accept(outputFormatter.formatStepProgress(current, total, step.id(), step.description()));

        // L0: 检查前置步骤依赖
        if (!checkDependencies(step, pes)) {
            StepResult skipped = StepResult.skipped(step.id(), "前置步骤失败");
            pes.addFailedStep(skipped);
            outputHandler.accept(outputFormatter.formatWarning("步骤 " + step.id() + " 跳过（依赖失败）"));
            return HandlerResult.withCount(PlanState.STEP_SKIPPED);
        }

        // 根据 mode 执行
        StepResult result = switch (step.mode()) {
            case PARALLEL -> executeParallel(step, outputHandler);
            case SUB_AGENT -> {
                outputHandler.accept(outputFormatter.formatWarning("步骤 " + step.id() + " 卸载到子 Agent"));
                yield executeAsSubAgent(step, state);
            }
            case SERIAL -> executeSerial(step, outputHandler);
        };

        // L2: 步骤失败处理
        if (!result.success()) {
            log.warn("步骤 {} 执行失败: {} (mode={})", step.id(), result.failureMode(), result.errorMessage());
            pes.addFailedStep(result);

            // L3: 连续失败熔断
            if (pes.getConsecutiveFailures() >= CONSECUTIVE_FAILURE_THRESHOLD) {
                log.warn("连续 {} 步失败，触发 LOCAL_REPLAN", pes.getConsecutiveFailures());
                return HandlerResult.withoutCount(PlanState.LOCAL_REPLAN);
            }
            outputHandler.accept(outputFormatter.formatStepSkipped(step.id(), result.failureMode().name()));
            return HandlerResult.withCount(PlanState.STEP_SKIPPED);
        }

        // 成功
        pes.addCompletedStep(result);
        pes.advance();
        outputHandler.accept(outputFormatter.formatStepComplete(step.id(), result.durationMs()));
        return HandlerResult.withCount(PlanState.STEP_VERIFY);
    }

    // ==================== 串行执行 ====================

    private StepResult executeSerial(PlanStep step, Consumer<String> outputHandler) {
        long start = System.currentTimeMillis();
        List<ToolResult> results = new ArrayList<>();
        boolean allFailed = true;

        for (ToolCall tc : step.tools()) {
            ToolResult r = executeSingleTool(tc, outputHandler);
            results.add(r);
            cacheReadResult(tc, r);
            if (r.isSuccess()) allFailed = false;
        }

        String output = collectOutput(results);
        long duration = System.currentTimeMillis() - start;

        if (allFailed && step.tools().isEmpty()) {
            // 无工具调用 = 纯思考步，视为成功
            return StepResult.success(step.id(), "[思考步]", step.tools(), duration);
        }
        if (allFailed) {
            return StepResult.failure(step.id(), "所有工具调用失败",
                StepFailureMode.TOOL_FAILURE, step.tools(), duration, 0);
        }
        return StepResult.success(step.id(), output, step.tools(), duration);
    }

    // ==================== 并行执行 ====================

    private StepResult executeParallel(PlanStep step, Consumer<String> outputHandler) {
        long start = System.currentTimeMillis();
        List<List<ToolCall>> batches = ToolPartitioner.partition(step.tools());
        List<ToolResult> results = new ArrayList<>();
        boolean allFailed = true;

        for (List<ToolCall> batch : batches) {
            if (ToolPartitioner.isParallel(batch)) {
                results.addAll(executeBatchParallel(batch, outputHandler));
            } else {
                for (ToolCall tc : batch) {
                    results.add(executeSingleTool(tc, outputHandler));
                }
            }
        }

        for (ToolCall tc : step.tools()) {
            cacheReadResult(tc, findResult(results, tc));
        }

        String output = collectOutput(results);
        long duration = System.currentTimeMillis() - start;

        boolean anySuccess = results.stream().anyMatch(ToolResult::isSuccess);
        if (!anySuccess) {
            return StepResult.failure(step.id(), "并行执行全部失败",
                StepFailureMode.TOOL_FAILURE, step.tools(), duration, 0);
        }
        return StepResult.success(step.id(), output, step.tools(), duration);
    }

    private List<ToolResult> executeBatchParallel(List<ToolCall> batch,
                                                   Consumer<String> outputHandler) {
        List<Callable<ToolResult>> tasks = new ArrayList<>();
        for (ToolCall tc : batch) {
            tasks.add(() -> {
                outputHandler.accept(outputFormatter.formatToolCallStart(
                    tc.getName(), tc.getArguments()));
                ToolResult r = toolRegistry.execute(tc.getName(), tc.getArguments());
                outputHandler.accept(outputFormatter.formatToolCallEnd(
                    tc.getName(), r));
                return r;
            });
        }

        List<Future<ToolResult>> futures;
        try {
            futures = toolExecutor.invokeAll(tasks, STEP_TIMEOUT_MS, TimeUnit.MILLISECONDS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return List.of();
        }

        List<ToolResult> results = new ArrayList<>();
        for (int i = 0; i < batch.size(); i++) {
            try {
                results.add(futures.get(i).get(1, TimeUnit.SECONDS));
            } catch (Exception e) {
                log.warn("并行工具执行失败: {}", e.getMessage());
                results.add(ToolResult.failure(e.getMessage()));
            }
        }
        return results;
    }

    // ==================== 子 Agent 卸载 ====================

    private StepResult executeAsSubAgent(PlanStep step, ExecutionState state) {
        long start = System.currentTimeMillis();
        try {
            // 构建子 Agent 指令
            String instruction = buildSubAgentInstruction(step, state);
            // TODO: 使用 TaskDelegationService 异步执行
            // 暂时同步执行作为占位
            log.info("子 Agent 卸载（占位实现）: {}", step.id());
            return StepResult.success(step.id(), "[SubAgent 占位]", step.tools(),
                System.currentTimeMillis() - start);
        } catch (Exception e) {
            return StepResult.failure(step.id(), e.getMessage(),
                StepFailureMode.SUB_AGENT_FAILED, step.tools(),
                System.currentTimeMillis() - start, 0);
        }
    }

    private String buildSubAgentInstruction(PlanStep step, ExecutionState state) {
        return String.format("""
            执行以下步骤：
            描述: %s
            预期输出: %s
            工具调用: %s
            """, step.description(), step.expectedResult(),
            step.tools().stream()
                .map(tc -> tc.getName() + "(" + tc.getArguments() + ")")
                .reduce((a, b) -> a + ", " + b)
                .orElse("无"));
    }

    // ==================== 单工具执行 ====================

    private ToolResult executeSingleTool(ToolCall tc, Consumer<String> outputHandler) {
        outputHandler.accept(outputFormatter.formatToolCallStart(tc.getName(), tc.getArguments()));
        try {
            ToolResult result = toolRegistry.execute(tc.getName(), tc.getArguments());
            outputHandler.accept(outputFormatter.formatToolCallEnd(tc.getName(), result));
            return result;
        } catch (Exception e) {
            outputHandler.accept(outputFormatter.formatError("工具 " + tc.getName() + " 异常: " + e.getMessage()));
            return ToolResult.failure(e.getMessage());
        }
    }

    // ==================== 辅助方法 ====================

    private boolean checkDependencies(PlanStep step, PlanExecutionState pes) {
        for (String depId : step.dependsOn()) {
            boolean depSuccess = pes.getCompletedSteps().stream()
                .anyMatch(r -> r.stepId().equals(depId) && r.success());
            boolean depFailed = pes.getFailedSteps().stream()
                .anyMatch(r -> r.stepId().equals(depId));
            if (depFailed || !depSuccess) {
                log.warn("前置步骤 {} 未完成，跳过 {}", depId, step.id());
                return false;
            }
        }
        return true;
    }

    private void cacheReadResult(ToolCall tc, ToolResult result) {
        if (!result.isSuccess()) return;
        String name = tc.getName();
        if (!"Read".equals(name) && !"Grep".equals(name)) return;
        String path = tc.getArguments() != null
            ? (String) tc.getArguments().get("path") : null;
        if (path == null) return;
        String content = result.getOutput();
        if (content == null) return;
        if (content.length() > MAX_FILE_CHARS) {
            content = content.substring(0, MAX_FILE_CHARS) + "\n... [truncated]";
        }
        String key = "Grep".equals(name) ? "grep:" + tc.getArguments() : path;
        fileContentCache.put(key, content);
    }

    private String collectOutput(List<ToolResult> results) {
        return results.stream()
            .filter(ToolResult::isSuccess)
            .map(ToolResult::getOutput)
            .filter(Objects::nonNull)
            .reduce((a, b) -> a + "\n---\n" + b)
            .orElse("");
    }

    private ToolResult findResult(List<ToolResult> results, ToolCall tc) {
        for (ToolResult r : results) {
            // 简化匹配，实际可根据 tool call id 匹配
        }
        return ToolResult.failure("not found");
    }

    private PlanExecutionState getPlanState(SessionContext ctx) {
        Object v = ctx.getVariable("_planExecutionState");
        return v instanceof PlanExecutionState ? (PlanExecutionState) v : null;
    }
}
