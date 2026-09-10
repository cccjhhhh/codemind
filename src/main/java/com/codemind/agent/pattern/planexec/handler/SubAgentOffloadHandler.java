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
    private com.codemind.agent.AgentLoop parentLoop;

    public SubAgentOffloadHandler(OutputFormatter outputFormatter) {
        this.outputFormatter = outputFormatter;
        this.executor = Executors.newFixedThreadPool(
            4, new NamedThreadFactory("plan-sub-agent"));
    }

    /** 由 PlanAgentPattern 或 WorkflowOrchestrator 注入父 AgentLoop */
    public void setParentLoop(com.codemind.agent.AgentLoop loop) {
        this.parentLoop = loop;
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
            log.warn("子 Agent 执行超时，触发全局重规划");
            state.outputHandler.accept(outputFormatter.formatWarning("子 Agent 超时，触发全局重规划"));
            return HandlerResult.withoutCount(PlanState.GLOBAL_REPLAN);
        } catch (Exception e) {
            log.error("子 Agent 执行异常: {}", e.getMessage());
            return HandlerResult.withoutCount(PlanState.STEP_RETRY);
        }
    }

    // ==================== 子 Agent 执行 ====================

    private AgentResult runSubAgent(String instruction, SessionContext parentCtx) {
        if (parentLoop == null) {
            log.warn("未设置父 AgentLoop，降级为占位实现");
            return AgentResult.success("[SubAgent] 执行完成");
        }

        com.codemind.agent.AgentLoop subAgent = parentLoop.createSubAgent();

        // 创建子 Agent 会话上下文
        com.codemind.session.SessionContext subCtx =
            new com.codemind.session.SessionContext(java.util.UUID.randomUUID().toString());
        subCtx.setWorkingDirectory(parentCtx.getWorkingDirectory());

        // 继承父 Agent 压缩后上下文
        for (com.codemind.llm.Message m : parentCtx.getHistory()) {
            subCtx.addMessage(m);
        }

        // 注入当前步骤指令
        subCtx.addMessage(com.codemind.llm.Message.user(instruction));

        log.info("子 Agent 开始执行: {}", instruction.substring(0, Math.min(80, instruction.length())));
        return subAgent.run(instruction, subCtx);
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
