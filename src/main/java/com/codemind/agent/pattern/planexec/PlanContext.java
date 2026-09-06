package com.codemind.agent.pattern.planexec;

import java.util.List;
import java.util.Map;

/**
 * 计划级共享上下文 — 跨步骤的共享状态。
 *
 * <p>用于在步骤之间传递共享变量、全局约束和执行策略提示。</p>
 *
 * <p>设计考量：</p>
 * <ul>
 *   <li>{@code sharedState}：步骤间共享的可变状态（如中间文件路径、临时变量）</li>
 *   <li>{@code globalConstraints}：全局约束列表（如"不修改 test 目录"）</li>
 *   <li>{@code strategy}：执行策略提示（如"优先使用并行"）</li>
 * </ul>
 */
public record PlanContext(

    /** 步骤间共享变量（Map<String, Object>） */
    Map<String, Object> sharedState,

    /** 全局约束列表（如安全约束、目录限制） */
    List<String> globalConstraints,

    /** 执行策略提示（指导 StepExecuteHandler 的行为） */
    String strategy
) {
    /** 空上下文 */
    public static final PlanContext EMPTY = new PlanContext(
        java.util.Collections.emptyMap(), java.util.Collections.emptyList(), null);
}
