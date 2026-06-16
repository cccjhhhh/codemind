package com.codemind.agent.statemachine.pattern;

/**
 * ReAct 范式的工作流转状态。
 *
 * <ul>
 *   <li>{@link #THINK} — LLM 推理 + 工具决策，下一步 ACT 或 COMPLETE</li>
 *   <li>{@link #ACT} — 执行 THINK 选定的工具</li>
 * </ul>
 */
public enum ReactState {
    THINK,
    ACT
}
