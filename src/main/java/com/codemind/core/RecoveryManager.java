package com.codemind.core;

import com.codemind.core.service.detection.LoopDetectionResult;
import com.codemind.core.service.detection.LoopDetector;
import com.codemind.core.service.recovery.ContinuationStrategy;
import com.codemind.core.service.recovery.FallbackStrategy;
import com.codemind.core.service.recovery.MaxTokensStrategy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Map;

/**
 * 集中管理跨迭代恢复状态与策略。
 *
 * 职责：
 * 1. max_tokens 三级升级（8K → 32K → 64K）— 委托 {@link MaxTokensStrategy}
 * 2. 续写次数追踪（最多 3 次） — 委托 {@link ContinuationStrategy}
 * 3. 连续 529 错误计数及 fallback 模型切换 — 委托 {@link FallbackStrategy}
 * 4. 循环检测 — 委托 {@link LoopDetector}
 *
 * 【harness 规则 09-recovery-scope】循环检测已独立为 LoopDetector。
 *
 * 生命周期：每个 AgentLoop.run() 调用创建一个新实例。
 */
public class RecoveryManager {

    private static final Logger log = LoggerFactory.getLogger(RecoveryManager.class);

    // ========== 策略委托 ==========

    private final MaxTokensStrategy maxTokensStrategy = new MaxTokensStrategy();
    private final ContinuationStrategy continuationStrategy = new ContinuationStrategy();
    private final FallbackStrategy fallbackStrategy = new FallbackStrategy();
    private final LoopDetector loopDetector = new LoopDetector();

    // ========== 兼容字段（待后续迁移） ==========

    private boolean hasAttemptedCompact = false;

    // ==================== max_tokens 升级 ====================

    public int getCurrentMaxTokens() {
        return maxTokensStrategy.getCurrentMaxTokens();
    }

    public int getMaxTokensStage() {
        return maxTokensStrategy.getStage();
    }

    public boolean escalateMaxTokens() {
        return maxTokensStrategy.escalate();
    }

    public boolean isMaxTokensExhausted() {
        return maxTokensStrategy.isExhausted();
    }

    // ==================== 续写 ====================

    public boolean recordContinuation() {
        return continuationStrategy.record();
    }

    public boolean isContinuationExhausted() {
        return continuationStrategy.isExhausted();
    }

    // ==================== 529 / fallback ====================

    public boolean record529() {
        return fallbackStrategy.record529();
    }

    public void reset529Count() {
        fallbackStrategy.reset529();
    }

    public void switchToFallback() {
        fallbackStrategy.switchToFallback();
    }

    public boolean isUsingFallback() {
        return fallbackStrategy.isUsingFallback();
    }

    public int getConsecutive529() {
        return fallbackStrategy.getConsecutive529();
    }

    // ==================== Reactive Compact ====================

    public boolean hasAttemptedCompact() {
        return hasAttemptedCompact;
    }

    public void setAttemptedCompact(boolean v) {
        this.hasAttemptedCompact = v;
    }

    // ==================== 循环检测 (委托 LoopDetector) ====================

    /**
     * 记录一次工具调用，检测循环模式。
     * 委托给 {@link LoopDetector} 完成实际检测。
     */
    public ContinueReason recordToolCall(String toolName, Map<String, Object> args) {
        LoopDetectionResult result = loopDetector.record(toolName, args);
        if (result.detected()) {
            log.warn("[LoopDetect] 循环检测触发! pattern={}", result.pattern());
            return ContinueReason.LOOP_DETECTED;
        }
        return null;
    }

    /**
     * 清空循环检测缓冲区。
     */
    public void clearRecentToolCalls() {
        loopDetector.clearBuffer();
    }

    /**
     * 设置循环检测冷却（LOOP_DETECTED 后调用）。
     */
    public void setLoopCooldown() {
        loopDetector.setCooldown();
    }

    // ==================== 重置 ====================

    public void onSuccessfulTurn() {
        reset529Count();
    }

    /**
     * 完全重置所有状态（新请求开始时调用）。
     */
    public void reset() {
        maxTokensStrategy.reset();
        continuationStrategy.reset();
        fallbackStrategy.reset();
        loopDetector.clearBuffer();
        hasAttemptedCompact = false;
    }
}
