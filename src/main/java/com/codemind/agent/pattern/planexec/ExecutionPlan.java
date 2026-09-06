package com.codemind.agent.pattern.planexec;

import java.util.List;

/**
 * 完整执行计划 — LLM 一次性生成，包含全局约束。
 *
 * <p>ExecutionPlan 是 PlanGenerateHandler 的输出，
 * 经 PlanValidateHandler 验证后注入到 {@link com.codemind.agent.engine.ExecutionState} 中。</p>
 *
 * <p>字段说明：</p>
 * <ul>
 *   <li>{@code goal} — 原始任务目标，用于重规划时传递上下文</li>
 *   <li>{@code steps} — 步骤序列（经拓扑排序后，满足依赖关系）</li>
 *   <li>{@code maxSteps} — 全局步数上限，防止 LLM 生成过多细粒度步骤</li>
 *   <li>{@code estimatedTokenBudget} — 预估 token 消耗，指导 PlanAwareCompactor 的压缩策略</li>
 *   <li>{@code context} — 计划级共享上下文，跨步骤传递共享状态和约束</li>
 * </ul>
 */
public record ExecutionPlan(

    /** 原始任务目标 */
    String goal,

    /** 步骤序列（拓扑排序后） */
    List<PlanStep> steps,

    /** 全局步数上限 */
    int maxSteps,

    /** 预估 token 预算（用于上下文压缩决策） */
    long estimatedTokenBudget,

    /** 计划级共享上下文 */
    PlanContext context,

    /** 计划创建时间（毫秒，用于超时检查） */
    long createdAtMs
) {
    /**
     * 从目标和步骤列表创建计划，使用默认值。
     */
    public ExecutionPlan(String goal, List<PlanStep> steps) {
        this(goal, steps, Math.max(steps.size() + 5, 20),
             50_000L, PlanContext.EMPTY, System.currentTimeMillis());
    }
}
