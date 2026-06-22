package com.codemind.agent.statemachine;

import com.codemind.agent.statemachine.pattern.ReactState;

/**
 * Agent 恢复 / 重试原因 — 非正常流转信号，由状态机统一处理。
 *
 * <ul>
 *   <li>{@link #RECOVERY_ESCALATE} — max_tokens 截断，升级到更大上下文后重试</li>
 *   <li>{@link #CONTINUATION} — max_tokens 已达上限仍需续写</li>
 *   <li>{@link #TOKEN_BUDGET_CONTINUE} — Token 预算压缩失败，提示 LLM 精简输出</li>
 *   <li>{@link #RETRY_BACKOFF} — 瞬态错误（429/529/5xx/timeout），指数退避后重试</li>
 *   <li>{@link #RECOVERY_FAILOVER} — 连续失败，切换到 fallback 模型</li>
 *   <li>{@link #LOOP_DETECTED} — 循环检测触发，强制摘要打断重复模式</li>
 *   <li>{@link #RECOVERY_COMPACT} — 空结果或 ContextLengthException，触发全量摘要</li>
 * </ul>
 *
 * <p>正常流转状态见 {@link ReactState 等范式模板}，
 * 终止态见 {@link TerminalState}。</p>
 */
public enum ContinueReason {
    RECOVERY_ESCALATE,
    CONTINUATION,
    TOKEN_BUDGET_CONTINUE,
    RETRY_BACKOFF,
    RECOVERY_FAILOVER,
    LOOP_DETECTED,
    RECOVERY_COMPACT
}
