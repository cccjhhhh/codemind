package com.codemind.agent.statemachine;

/**
 * Agent 终止态 — 所有范式共享。
 *
 * <ul>
 *   <li>{@link #COMPLETE} — LLM 回复完成，Agent 任务结束</li>
 *   <li>{@link #MAX_ITERATIONS} — 达到 maxIterations 上限，Agent 终止</li>
 *   <li>{@link #ERROR} — 不可恢复的错误，Agent 终止</li>
 *   <li>{@link #USER_INTERRUPT} — 用户手动中断，Agent 终止</li>
 * </ul>
 */
public enum TerminalState {
    COMPLETE,
    MAX_ITERATIONS,
    ERROR,
    USER_INTERRUPT
}
