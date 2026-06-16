package com.codemind.agent.detection;

/**
 * 循环检测结果。
 *
 * @param detected   是否检测到循环
 * @param confidence 置信度 (0.0 ~ 1.0)
 * @param pattern    检测到的模式描述
 * @param toolName   触发检测的工具名
 * @param repeatedCount 连续重复次数
 */
public record LoopDetectionResult(
        boolean detected,
        double confidence,
        String pattern,
        String toolName,
        int repeatedCount
) {
    public static final LoopDetectionResult NEGATIVE = new LoopDetectionResult(
            false, 0.0, "", null, 0);

    public static LoopDetectionResult positive(String pattern, String toolName, int count) {
        return new LoopDetectionResult(true, 0.9, pattern, toolName, count);
    }
}
