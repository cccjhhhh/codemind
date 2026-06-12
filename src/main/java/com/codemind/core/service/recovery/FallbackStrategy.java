package com.codemind.core.service.recovery;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Fallback 切换策略。
 *
 * 追踪连续 529 错误次数，达到阈值后切换到 fallback 模型。
 */
public class FallbackStrategy {

    private static final Logger log = LoggerFactory.getLogger(FallbackStrategy.class);

    private static final int FALLBACK_THRESHOLD = 3;

    private int consecutive529 = 0;
    private boolean isFallbackModel = false;

    /**
     * 记录一次 529 错误。
     *
     * @return true 如果达到 fallback 切换阈值
     */
    public boolean record529() {
        consecutive529++;
        if (consecutive529 >= FALLBACK_THRESHOLD) {
            log.warn("连续 {} 次 529，需要 fallback 模型", consecutive529);
            return true;
        }
        return false;
    }

    /**
     * 重置 529 计数（成功调用后调用）。
     */
    public void reset529() {
        consecutive529 = 0;
    }

    /**
     * 切换到 fallback 模型。
     */
    public void switchToFallback() {
        isFallbackModel = true;
        consecutive529 = 0;
        log.info("切换到 fallback 模型");
    }

    public boolean isUsingFallback() {
        return isFallbackModel;
    }

    public int getConsecutive529() {
        return consecutive529;
    }

    public void reset() {
        consecutive529 = 0;
        isFallbackModel = false;
    }
}
