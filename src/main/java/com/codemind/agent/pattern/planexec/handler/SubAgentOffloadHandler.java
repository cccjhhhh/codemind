package com.codemind.agent.pattern.planexec.handler;

import com.codemind.agent.engine.ExecutionState;
import com.codemind.agent.pattern.planexec.*;
import com.codemind.agent.spi.AgentResult;
import com.codemind.agent.statemachine.HandlerResult;
import com.codemind.agent.statemachine.StateHandler;
import com.codemind.frontend.output.spi.OutputFormatter;
import com.codemind.llm.Message;
import com.codemind.llm.ToolCall;
import com.codemind.session.SessionContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.concurrent.*;

/**
 * SubAgentOffloadHandler — 将复杂/低信心步骤卸载到独立子 Agent。
 *
 * <p>执行策略：</p>
 * <ul>
 *   <li>子 Agent 成功 → STEP_VERIFY</li>
 *   <li>子 Agent 失败 → STEP_RETRY（降级为单步重试）</li>
 *   <li>子 Agent 超时 → LOCAL_REPLAN</li>
 * </ul>
 *
 * <p>当前为占位实现，实际可接入 {@code TaskDelegationService}。</p>
 */
public class SubAgentOffloadHandler implements StateHandler {

    private static final Logger log = LoggerFactory.getLogger(SubAgentOffloadHandler.class);
    private static final int SUB_AGENT_TIMEOUT_SECONDS = 60;

    private final OutputFormatter outputFormatter;
    private final ExecutorService executor;

    public SubAgentOffloadHandler(OutputFormatter outputFormatter) {
        this.outputFormatter = outputFormatter;
        this.executor = Executors.newFixedThreadPool(
            4, new NamedThreadFactory("plan-sub-agent"));
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
            return HandlerResult.withCount(PlanState.PLAN_COMPLETE);
        }

        state.outputHandler.accept(outputFormatter.formatWarning(
            String.format("[%s] 卸载到子 Agent: %s", step.id(), step.description())));

        String instruction = buildInstruction(step, state);

        try {
            Future<AgentResult> future = executor.submit(() ->
                runSubAgent(instruction, ctx));

            AgentResult result = future.get(SUB_AGENT_TIMEOUT_SECONDS, TimeUnit.SECONDS);

            if (result.isSuccess()) {
                ctx.addMessage(Message.assistant("[SubAgent 结果]\n" + result.getMessage()));
                log.info("子 Agent 执行成功: {}", step.id());
                return HandlerResult.withCount(PlanState.STEP_VERIFY);
            } else {
                log.warn("子 Agent 执行失败，降级为重试: {}", result.getError());
                state.outputHandler.accept(outputFormatter.formatWarning(
                    "子 Agent 失败: " + result.getError() + "，降级重试"));
                return HandlerResult.withoutCount(PlanState.STEP_RETRY);
            }
        } catch (TimeoutException e) {
            log.warn("子 Agent 执行超时，触发局部重规划");
            state.outputHandler.accept(outputFormatter.formatWarning("子 Agent 超时，触发重规划"));
            return HandlerResult.withoutCount(PlanState.LOCAL_REPLAN);
        } catch (Exception e) {
            log.error("子 Agent 执行异常: {}", e.getMessage());
            return HandlerResult.withoutCount(PlanState.STEP_RETRY);
        }
    }

    // ==================== 子 Agent 执行 ====================

    private AgentResult runSubAgent(String instruction, SessionContext parentCtx) {
        // TODO: 实际实现应创建独立的 AgentLoop 实例
        // 当前占位：直接返回成功，模拟子 Agent 结果
        log.info("子 Agent 执行占位: {}", instruction.substring(0, Math.min(100, instruction.length())));
        return AgentResult.success("[SubAgent 占位结果] 执行完成");
    }

    private String buildInstruction(PlanStep step, ExecutionState state) {
        return String.format("""
            执行以下步骤，返回最终结果：

            【步骤描述】
            %s

            【预期输出】
            %s

            【工具调用】
            %s
            """,
            step.description(),
            step.expectedResult() != null ? step.expectedResult() : "无特定要求",
            formatToolCalls(step.tools())
        );
    }

    private String formatToolCalls(java.util.List<ToolCall> tools) {
        if (tools == null || tools.isEmpty()) return "无";
        return tools.stream()
            .map(tc -> tc.getName() + "(" + tc.getArguments() + ")")
            .reduce((a, b) -> a + ", " + b)
            .orElse("");
    }

    // ==================== 辅助方法 ====================

    private PlanExecutionState getPlanState(SessionContext ctx) {
        Object v = ctx.getVariable("_planExecutionState");
        return v instanceof PlanExecutionState ? (PlanExecutionState) v : null;
    }

    /** 简单的线程工厂（占位，实际应使用 ThreadPoolConfig） */
    private static class NamedThreadFactory implements ThreadFactory {
        private final String name;
        private int count;
        NamedThreadFactory(String name) { this.name = name; }
        @Override
        public Thread newThread(Runnable r) {
            Thread t = new Thread(r, name + "-" + count++);
            t.setDaemon(true);
            return t;
        }
    }
}
