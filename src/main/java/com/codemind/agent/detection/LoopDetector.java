package com.codemind.agent.detection;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.LinkedList;
import java.util.Map;

/**
 * 循环检测器。
 *
 * 独立于 RecoveryManager，专注检测 Agent 是否陷入重复操作模式。
 * 检测策略可插拔（策略模式），当前实现为"连续重复检测" + "频率检测"。
 *
 * 【harness 规则 09-recovery-scope】
 * - 只负责检测算法，不关心 AgentLoop 状态转移
 * - 输入：工具调用流 → 输出：LoopDetectionResult
 * - 缓冲区大小、统一阈值、冷却步数可配置
 */
public class LoopDetector {

    private static final Logger log = LoggerFactory.getLogger(LoopDetector.class);

    /** 循环检测窗口大小 */
    private final int bufferSize;

    /** 循环判定阈值：连续相同调用次数 */
    private final int threshold;

    /** 冷却步数：触发 LOOP_DETECTED 后跳过检测的步数 */
    private final int cooldownSteps;

    /** 冷却计数器：>0 时跳过检测 */
    private int cooldownRemaining = 0;

    /** 环形缓冲区：最近 N 条工具调用记录 */
    private final LinkedList<ToolCallRecord> recentToolCalls = new LinkedList<>();

    /** 频率检测阈值：同一工具在窗口内调用次数超过此值触发警告 */
    private static final int FREQUENCY_WARNING_THRESHOLD = 8;

    /** 频率检测阈值：同一工具在窗口内调用次数超过此值触发循环检测 */
    private static final int FREQUENCY_LOOP_THRESHOLD = 12;

    /** 模式检测：检测重复模式（如 A-B-A-B） */
    private static final int PATTERN_DETECTION_WINDOW = 6;

    public LoopDetector() {
        this(20, 3, 15);
    }

    public LoopDetector(int bufferSize, int threshold, int cooldownSteps) {
        this.bufferSize = bufferSize;
        this.threshold = threshold;
        this.cooldownSteps = cooldownSteps;
    }

    /**
     * 记录一次工具调用并检测循环。
     *
     * @return LoopDetectionResult，detected=true 表示检测到循环
     */
    public LoopDetectionResult record(String toolName, Map<String, Object> args) {
        String digest = digestArgs(toolName, args);
        recentToolCalls.addLast(new ToolCallRecord(toolName, digest));
        if (recentToolCalls.size() > bufferSize) {
            recentToolCalls.removeFirst();
        }

        // 冷却期跳过检测
        if (cooldownRemaining > 0) {
            cooldownRemaining--;
            return LoopDetectionResult.NEGATIVE;
        }

        // 多策略检测
        LoopDetectionResult result = detect(toolName);

        // 如果连续重复检测未触发，尝试频率检测
        if (!result.detected()) {
            result = detectByFrequency(toolName);
        }

        // 如果频率检测未触发，尝试模式检测
        if (!result.detected()) {
            result = detectByPattern();
        }

        return result;
    }

    /**
     * 设置冷却（LOOP_DETECTED 后调用）。
     */
    public void setCooldown() {
        this.cooldownRemaining = cooldownSteps;
        log.info("[LoopDetect] 设置冷却: {} 步内跳过检测", cooldownSteps);
    }

    /**
     * 清空缓冲区。
     */
    public void clearBuffer() {
        recentToolCalls.clear();
    }

    // ==================== 内部实现 ====================

    private LoopDetectionResult detect(String toolName) {
        if (recentToolCalls.size() < threshold) {
            return LoopDetectionResult.NEGATIVE;
        }

        // 检查尾部连续重复
        int size = recentToolCalls.size();
        ToolCallRecord last = recentToolCalls.getLast();
        String lastKey = last.toolName + ":" + last.argsDigest;

        for (int i = size - 1; i >= size - threshold; i--) {
            ToolCallRecord r = recentToolCalls.get(i);
            String currentKey = r.toolName + ":" + r.argsDigest;
            if (!currentKey.equals(lastKey)) {
                return LoopDetectionResult.NEGATIVE;
            }
        }

        log.warn("检测到连续循环: '{}' 连续 {} 次 (阈值 {})", lastKey, threshold, threshold);
        return LoopDetectionResult.positive(lastKey, toolName, threshold);
    }

    /**
     * 频率检测：同一工具在窗口内调用次数过多
     */
    private LoopDetectionResult detectByFrequency(String toolName) {
        if (recentToolCalls.size() < FREQUENCY_WARNING_THRESHOLD) {
            return LoopDetectionResult.NEGATIVE;
        }

        // 统计同一工具的调用次数
        int count = 0;
        for (ToolCallRecord record : recentToolCalls) {
            if (record.toolName.equals(toolName)) {
                count++;
            }
        }

        if (count >= FREQUENCY_LOOP_THRESHOLD) {
            log.warn("检测到频率循环: '{}' 在窗口内调用 {} 次 (阈值 {})",
                toolName, count, FREQUENCY_LOOP_THRESHOLD);
            return LoopDetectionResult.positive(toolName + ":frequency", toolName, count);
        }

        if (count >= FREQUENCY_WARNING_THRESHOLD) {
            log.warn("检测到高频调用: '{}' 在窗口内调用 {} 次 (警告阈值 {})",
                toolName, count, FREQUENCY_WARNING_THRESHOLD);
        }

        return LoopDetectionResult.NEGATIVE;
    }

    /**
     * 模式检测：检测重复模式（如 A-B-A-B）
     */
    private LoopDetectionResult detectByPattern() {
        if (recentToolCalls.size() < PATTERN_DETECTION_WINDOW) {
            return LoopDetectionResult.NEGATIVE;
        }

        // 检测 A-B-A-B 模式
        for (int patternLen = 2; patternLen <= PATTERN_DETECTION_WINDOW / 2; patternLen++) {
            boolean isPattern = true;
            for (int i = recentToolCalls.size() - patternLen; i < recentToolCalls.size(); i++) {
                ToolCallRecord current = recentToolCalls.get(i);
                ToolCallRecord pattern = recentToolCalls.get(i - patternLen);
                if (!current.toolName.equals(pattern.toolName) ||
                    !current.argsDigest.equals(pattern.argsDigest)) {
                    isPattern = false;
                    break;
                }
            }

            if (isPattern) {
                String patternKey = recentToolCalls.get(recentToolCalls.size() - patternLen).toolName
                    + ":" + recentToolCalls.get(recentToolCalls.size() - patternLen).argsDigest;
                log.warn("检测到模式循环: 模式长度 {} 重复出现 (模式: {})", patternLen, patternKey);
                return LoopDetectionResult.positive(patternKey + ":pattern", patternKey.split(":")[0], patternLen);
            }
        }

        return LoopDetectionResult.NEGATIVE;
    }

    private String digestArgs(String toolName, Map<String, Object> args) {
        if (args == null) return toolName + ":null";
        return switch (toolName) {
            case "Read" -> {
                String path = (String) args.get("path");
                Integer offset = (Integer) args.get("offset");
                Integer limit = (Integer) args.get("limit");
                yield toolName + ":" + (path != null ? path : "?")
                        + "#" + (offset != null ? offset : 0)
                        + "-" + (limit != null ? limit : "");
            }
            case "Write", "Edit" -> {
                String path = (String) args.get("path");
                yield toolName + ":" + (path != null ? path : "?");
            }
            case "Glob" -> {
                String pattern = (String) args.get("pattern");
                String gpath = (String) args.get("path");
                yield toolName + ":" + (pattern != null ? pattern : "?")
                        + "@" + (gpath != null ? gpath : ".");
            }
            case "Grep" -> {
                String pattern = (String) args.get("pattern");
                String gpath = (String) args.get("path");
                yield toolName + ":" + (pattern != null ? pattern : "?")
                        + "@" + (gpath != null ? gpath : ".");
            }
            case "Bash" -> {
                String cmd = (String) args.get("command");
                yield toolName + ":" + (cmd != null ?
                        cmd.substring(0, Math.min(cmd.length(), 60)) : "?");
            }
            case "Task" -> {
                String instruction = (String) args.get("instruction");
                yield toolName + ":" + (instruction != null ?
                        instruction.substring(0, Math.min(instruction.length(), 60)) : "?");
            }
            case "Todo" -> {
                String operation = (String) args.get("operation");
                String itemId = (String) args.get("item_id");
                String goal = (String) args.get("goal");
                yield toolName + ":" + (operation != null ? operation : "?")
                        + "#" + (itemId != null ? itemId : (goal != null ? goal : ""));
            }
            case "WebFetch" -> {
                String url = (String) args.get("url");
                yield toolName + ":" + (url != null ?
                        url.substring(0, Math.min(url.length(), 60)) : "?");
            }
            case "LoadSkill" -> {
                String name = (String) args.get("name");
                yield toolName + ":" + (name != null ? name : "?");
            }
            default -> toolName;
        };
    }

    private record ToolCallRecord(String toolName, String argsDigest) {}
}
