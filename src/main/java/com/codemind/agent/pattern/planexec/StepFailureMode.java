package com.codemind.agent.pattern.planexec;

/**
 * 步骤失败的根因分类。
 *
 * <p>精确分类失败原因，使重规划策略能针对不同失败模式选择最优恢复方式。</p>
 *
 * <ul>
 *   <li>{@link #TOOL_FAILURE} — 工具调用返回错误，可重试或跳过</li>
 *   <li>{@link #TIMEOUT} — 步骤执行超时，触发重规划</li>
 *   <li>{@link #CONTEXT_OVERFLOW} — 上下文溢出，需要压缩后重试</li>
 *   <li>{@link #VALIDATION_FAILED} — 验证不通过，需要重新生成步骤</li>
 *   <li>{@link #DEPENDENCY_FAILED} — 前置步骤失败，直接跳过</li>
 *   <li>{@link #SUB_AGENT_FAILED} — 子 Agent 执行失败，降级重试</li>
 * </ul>
 */
public enum StepFailureMode {
    TOOL_FAILURE,
    TIMEOUT,
    CONTEXT_OVERFLOW,
    VALIDATION_FAILED,
    DEPENDENCY_FAILED,
    SUB_AGENT_FAILED
}
