package com.codemind.agent.statemachine;

import com.codemind.agent.statemachine.ContinueReason;
import com.codemind.agent.statemachine.TerminalState;
import com.codemind.agent.statemachine.pattern.ReactState;

/**
 * Handler 执行结果，携带下一步状态。
 * <p>{@code nextReason} 字段为 {@link Object} 类型，运行时实际为三个枚举之一：
 * {@link ReactState}、{@link TerminalState}、{@link ContinueReason}。</p>
 */
public record HandlerResult(Object nextReason, boolean countTurn) {

    // === withCount 工厂（占用一次迭代计数） ===

    public static HandlerResult withCount(ReactState state) {
        return new HandlerResult(state, true);
    }

    public static HandlerResult withCount(TerminalState state) {
        return new HandlerResult(state, true);
    }

    public static HandlerResult withCount(ContinueReason reason) {
        return new HandlerResult(reason, true);
    }

    // === withoutCount 工厂（不占用迭代计数） ===

    public static HandlerResult withoutCount(ReactState state) {
        return new HandlerResult(state, false);
    }

    public static HandlerResult withoutCount(TerminalState state) {
        return new HandlerResult(state, false);
    }

    public static HandlerResult withoutCount(ContinueReason reason) {
        return new HandlerResult(reason, false);
    }
}
