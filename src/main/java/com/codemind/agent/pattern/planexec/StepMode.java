package com.codemind.agent.pattern.planexec;

/**
 * 步骤执行模式。
 *
 * <p>决定 StepExecuteHandler 如何执行步骤内的工具调用。</p>
 *
 * <ul>
 *   <li>{@link #SERIAL} — 顺序执行所有工具调用（默认）</li>
 *   <li>{@link #PARALLEL} — 并行执行所有工具调用（独立工具无依赖时）</li>
 *   <li>{@link #SUB_AGENT} — 将整个步骤卸载到独立子 Agent 执行（高风险步骤）</li>
 * </ul>
 */
public enum StepMode {
    SERIAL,
    PARALLEL,
    SUB_AGENT
}
