package com.codemind.agent.engine;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * 熔断器：防止 Agent 陷入无限循环或长时间无响应。
 *
 * 状态：
 * - CLOSED：正常状态，允许执行
 * - OPEN：熔断状态，拒绝执行，等待冷却时间
 * - HALF_OPEN：半开状态，允许少量测试请求
 *
 * 触发条件：
 * - 连续失败次数超过阈值
 * - 或连续重复工具调用超过阈值
 */
public class CircuitBreaker {

    private static final Logger log = LoggerFactory.getLogger(CircuitBreaker.class);

    /** 状态枚举 */
    public enum State {
        CLOSED,    // 正常
        OPEN,      // 熔断
        HALF_OPEN  // 半开
    }

    /** 当前状态 */
    private volatile State state = State.CLOSED;

    /** 连续失败计数 */
    private int failureCount = 0;

    /** 连续重复工具调用计数 */
    private int consecutiveRepeatCount = 0;

    /** 上次熔断时间 */
    private long lastOpenTime = 0;

    /** 配置参数 */
    private final int failureThreshold;      // 失败阈值
    private final int repeatThreshold;       // 重复阈值
    private final long cooldownMs;           // 冷却时间（毫秒）
    private final int halfOpenMaxAttempts;   // 半开状态最大尝试次数

    /** 半开状态尝试计数 */
    private int halfOpenAttempts = 0;

    public CircuitBreaker() {
        this(5, 8, 30_000, 2);
    }

    public CircuitBreaker(int failureThreshold, int repeatThreshold,
                          long cooldownMs, int halfOpenMaxAttempts) {
        this.failureThreshold = failureThreshold;
        this.repeatThreshold = repeatThreshold;
        this.cooldownMs = cooldownMs;
        this.halfOpenMaxAttempts = halfOpenMaxAttempts;
    }

    /**
     * 检查是否允许执行
     */
    public boolean allowExecution() {
        switch (state) {
            case CLOSED:
                return true;
            case OPEN:
                // 检查冷却时间是否已过
                if (System.currentTimeMillis() - lastOpenTime >= cooldownMs) {
                    state = State.HALF_OPEN;
                    halfOpenAttempts = 0;
                    log.info("熔断器: OPEN → HALF_OPEN (冷却时间已过)");
                    return true;
                }
                return false;
            case HALF_OPEN:
                return halfOpenAttempts < halfOpenMaxAttempts;
            default:
                return false;
        }
    }

    /**
     * 记录成功执行
     */
    public void recordSuccess() {
        switch (state) {
            case CLOSED:
                failureCount = 0;
                consecutiveRepeatCount = 0;
                break;
            case HALF_OPEN:
                halfOpenAttempts++;
                if (halfOpenAttempts >= halfOpenMaxAttempts) {
                    state = State.CLOSED;
                    failureCount = 0;
                    consecutiveRepeatCount = 0;
                    log.info("熔断器: HALF_OPEN → CLOSED (测试成功)");
                }
                break;
        }
    }

    /**
     * 记录失败执行
     */
    public void recordFailure() {
        switch (state) {
            case CLOSED:
                failureCount++;
                if (failureCount >= failureThreshold) {
                    trip("连续失败 " + failureCount + " 次");
                }
                break;
            case HALF_OPEN:
                // 半开状态失败，立即熔断
                trip("半开状态测试失败");
                break;
        }
    }

    /**
     * 记录重复工具调用
     */
    public void recordRepeat() {
        consecutiveRepeatCount++;
        if (consecutiveRepeatCount >= repeatThreshold) {
            trip("连续重复工具调用 " + consecutiveRepeatCount + " 次");
        }
    }

    /**
     * 重置重复计数器（当工具调用变化时）
     */
    public void resetRepeatCount() {
        consecutiveRepeatCount = 0;
    }

    /**
     * 触发熔断
     */
    private void trip(String reason) {
        state = State.OPEN;
        lastOpenTime = System.currentTimeMillis();
        log.warn("熔断器触发: {} (状态: CLOSED → OPEN)", reason);
    }

    /**
     * 手动重置熔断器
     */
    public void reset() {
        state = State.CLOSED;
        failureCount = 0;
        consecutiveRepeatCount = 0;
        halfOpenAttempts = 0;
        log.info("熔断器手动重置");
    }

    /**
     * 获取当前状态
     */
    public State getState() {
        return state;
    }

    /**
     * 获取统计信息
     */
    public String getStats() {
        return String.format("State=%s, Failures=%d, Repeats=%d, HalfOpenAttempts=%d",
            state, failureCount, consecutiveRepeatCount, halfOpenAttempts);
    }
}
