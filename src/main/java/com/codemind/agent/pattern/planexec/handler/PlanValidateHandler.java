package com.codemind.agent.pattern.planexec.handler;

import com.codemind.agent.engine.ExecutionState;
import com.codemind.agent.pattern.planexec.*;
import com.codemind.agent.statemachine.HandlerResult;
import com.codemind.agent.statemachine.StateHandler;
import com.codemind.llm.ToolCall;
import com.codemind.llm.ToolDefinition;
import com.codemind.session.SessionContext;
import com.codemind.tool.ToolRegistry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;
import java.util.stream.Collectors;

/**
 * PlanValidateHandler — 验证执行计划的合法性。
 *
 * <p>执行前静态分析，发现缺陷后触发重规划而非运行时失败：</p>
 * <ul>
 *   <li>循环依赖检测（拓扑排序）</li>
 *   <li>步数上限检查</li>
 *   <li>工具可用性检查</li>
 *   <li>依赖完整性检查</li>
 * </ul>
 *
 * <p>验证通过 → STEP_EXECUTE；验证失败 → LOCAL_REPLAN。</p>
 */
public class PlanValidateHandler implements StateHandler {

    private static final Logger log = LoggerFactory.getLogger(PlanValidateHandler.class);
    private final ToolRegistry toolRegistry;

    public PlanValidateHandler(ToolRegistry toolRegistry) {
        this.toolRegistry = toolRegistry;
    }

    @Override
    public HandlerResult handle(ExecutionState state) {
        SessionContext ctx = state.sessionContext;
        PlanExecutionState pes = getPlanState(ctx);
        if (pes == null || pes.getPlan() == null) {
            log.warn("未找到执行计划，跳过验证");
            return HandlerResult.withCount(PlanState.STEP_EXECUTE);
        }

        ExecutionPlan plan = pes.getPlan();
        List<PlanStep> steps = plan.steps();

        // 1. 循环依赖检测
        List<String> cycle = detectCycle(steps);
        if (!cycle.isEmpty()) {
            log.warn("计划存在循环依赖: {}", cycle);
            return HandlerResult.withoutCount(PlanState.LOCAL_REPLAN);
        }

        // 2. 步数上限检查
        if (steps.size() > plan.maxSteps()) {
            log.warn("步骤数 {} 超过上限 {}，截断", steps.size(), plan.maxSteps());
            steps = steps.subList(0, plan.maxSteps());
            plan = new ExecutionPlan(plan.goal(), steps, plan.maxSteps(),
                plan.estimatedTokenBudget(), plan.context(), plan.createdAtMs());
            pes.updatePlan(plan);
        }

        // 3. 工具可用性检查
        List<String> missingTools = steps.stream()
            .flatMap(s -> s.tools().stream())
            .map(ToolCall::getName)
            .distinct()
            .filter(name -> toolRegistry.getDefinition(name) != null)
            .collect(Collectors.toList());
        if (!missingTools.isEmpty()) {
            log.warn("计划引用了未注册工具: {}", missingTools);
            return HandlerResult.withoutCount(PlanState.LOCAL_REPLAN);
        }

        // 4. 依赖完整性检查
        Set<String> stepIds = steps.stream().map(PlanStep::id).collect(Collectors.toSet());
        for (PlanStep step : steps) {
            for (String dep : step.dependsOn()) {
                if (!stepIds.contains(dep)) {
                    log.warn("步骤 {} 依赖不存在的步骤: {}", step.id(), dep);
                    return HandlerResult.withoutCount(PlanState.LOCAL_REPLAN);
                }
            }
        }

        log.info("计划验证通过：{} 个步骤", steps.size());
        return HandlerResult.withCount(PlanState.STEP_EXECUTE);
    }

    // ==================== 循环依赖检测（Kahn 算法） ====================

    private List<String> detectCycle(List<PlanStep> steps) {
        Set<String> ids = steps.stream().map(PlanStep::id).collect(Collectors.toSet());
        Map<String, List<String>> adj = new HashMap<>();
        Map<String, Integer> inDegree = new HashMap<>();

        for (PlanStep s : steps) {
            adj.put(s.id(), new ArrayList<>(s.dependsOn()));
            inDegree.put(s.id(), s.dependsOn().size());
        }

        Queue<String> queue = new LinkedList<>();
        for (var entry : inDegree.entrySet()) {
            if (entry.getValue() == 0) queue.offer(entry.getKey());
        }

        int visited = 0;
        while (!queue.isEmpty()) {
            String curr = queue.poll();
            visited++;
            for (PlanStep s : steps) {
                if (s.dependsOn().contains(curr)) {
                    inDegree.merge(s.id(), -1, Integer::sum);
                    if (inDegree.get(s.id()) == 0) {
                        queue.offer(s.id());
                    }
                }
            }
        }

        return visited < steps.size()
            ? steps.stream().map(PlanStep::id).filter(id -> inDegree.getOrDefault(id, 0) > 0)
                   .collect(Collectors.toList())
            : List.of();
    }

    // ==================== 辅助方法 ====================

    private PlanExecutionState getPlanState(SessionContext ctx) {
        Object v = ctx.getVariable("_planExecutionState");
        return v instanceof PlanExecutionState ? (PlanExecutionState) v : null;
    }
}
