package com.codemind.agent.pattern.planexec;

/**
 * LLM 对执行步骤的信心评估。
 *
 * <p>在 PlanGenerateHandler 生成计划时，LLM 同步评估每步的执行难度和确定性。</p>
 *
 * <ul>
 *   <li>{@link #HIGH} — 步骤简单明确，可直接串行执行</li>
 *   <li>{@link #MEDIUM} — 步骤有一定复杂度，常规执行即可</li>
 *   <li>{@link #LOW} — 步骤不确定性强，自动启用 SUB_AGENT 模式隔离风险</li>
 * </ul>
 */
public enum StepConfidence {
    HIGH,
    MEDIUM,
    LOW
}
