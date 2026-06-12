package com.codemind.core;

/**
 * Agent 主循环的状态转移原因 / 下一步动作。
 *
 * 语义分组：
 * ─ 正常流转 — THINK → ACT → THINK / COMPLETE
 * ─ 模型侧恢复 — RECOVERY_ESCALATE / CONTINUATION / TOKEN_BUDGET_CONTINUE
 * ─ 网络侧恢复 — RETRY_BACKOFF
 * ─ 工具侧恢复 — RECOVERY_FAILOVER / LOOP_DETECTED
 * ─ 上下文恢复 — RECOVERY_COMPACT
 * ─ 终止 — MAX_ITERATIONS / ERROR / USER_INTERRUPT
 */
public enum ContinueReason {

    // ========== 正常 ==========

    /** THINK: LLM 推理 + 工具决策 → 下一步 ACT 或 COMPLETE */
    THINK,

    /** ACT: 执行 THINK 选定的工具 */
    ACT,

    /** LLM 回复完成，Agent 任务结束 */
    COMPLETE,

    // ========== max_tokens 截断恢复 ==========

    /**
     * max_tokens 截断，但还有升级空间。
     * RecoveryManager 已将 max_tokens 升到下一级（8K→32K→64K），
     * 需要重新发起 LLM 请求。
     */
    RECOVERY_ESCALATE,

    /**
     * max_tokens 已达最高级仍然截断，追加续写提示让 LLM 继续。
     * RecoveryManager 控制最多续写 3 次。
     */
    CONTINUATION,

    /**
     * Token 预算压缩失败且预算仍紧张。
     * 添加精简回复提示，让 LLM 主动控制输出长度以避免 ContextLengthException。
     */
    TOKEN_BUDGET_CONTINUE,

    // ========== 网络错误恢复 ==========

    /**
     * 瞬态错误（429 rate limit / 529 overloaded / 5xx / timeout）。
     * 指数退避 + jitter 后重试同一轮（不增加 iterationCount）。
     */
    RETRY_BACKOFF,

    // ========== 工具侧恢复 ==========

    /**
     * 连续多轮工具执行全部失败，尝试切换到 fallback 模型。
     * RecoveryManager 追踪连续 529 / 连续工具失败次数。
     */
    RECOVERY_FAILOVER,

    /**
     * 循环检测触发：12 轮窗口内同一工具+路径出现 ≥4 次。
     * 强制 L4 摘要打断重复模式，让 LLM 换策略。
     */
    LOOP_DETECTED,

    // ========== 上下文压缩 ==========

    /**
     * LLM 返回空结果或 ContextLengthException。
     * 触发 L4 全量摘要 (compactHistory)，然后重试。
     * 摘要也失败则转为 ERROR。
     */
    RECOVERY_COMPACT,

    // ========== 终止 ==========

    /** 达到 maxIterations 上限，Agent 终止 */
    MAX_ITERATIONS,

    /** 不可恢复的错误，Agent 终止 */
    ERROR,

    /** 用户手动中断，Agent 终止 */
    USER_INTERRUPT
}
