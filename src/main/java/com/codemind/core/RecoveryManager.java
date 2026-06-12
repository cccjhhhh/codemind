package com.codemind.core;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.HashMap;
import java.util.LinkedList;
import java.util.Map;

/**
 * 集中管理跨迭代恢复状态与策略。
 *
 * 职责：
 * 1. max_tokens 三级升级（8K → 32K → 64K）
 * 2. 续写次数追踪（最多 3 次）
 * 3. 连续 529 错误计数及 fallback 模型切换
 * 4. 循环检测（连续重复检测 + 工具差异化阈值）
 * 5. reactive compact 标记（是否已尝试过压缩）
 *
 * 生命周期：每个 AgentLoop.run() 调用创建一个新实例。
 */
public class RecoveryManager {

    private static final Logger log = LoggerFactory.getLogger(RecoveryManager.class);

    // ========== 常量 ==========

    /** max_tokens 三级升级 */
    private static final int[] MAX_TOKENS_STAGES = { 8192, 32768, 65536 };

    private static final int DEFAULT_MAX_TOKENS = 8192;

    /** 最大续写次数 */
    private static final int MAX_CONTINUATIONS = 3;

    /** fallback 切换阈值：连续 529 次数 */
    private static final int FALLBACK_AFTER_CONSECUTIVE_529 = 3;

    /** 循环检测窗口大小 */
    private static final int LOOP_BUFFER_SIZE = 15;

    /** 默认循环判定阈值：连续相同调用次数 */
    private static final int DEFAULT_LOOP_THRESHOLD = 3;

    // ========== 状态 ==========

    private int maxTokensStage = 0;  // 0=未升级, 1=32K, 2=64K
    private int currentMaxTokens = DEFAULT_MAX_TOKENS;
    private int continuationCount = 0;
    private int consecutive529 = 0;
    private boolean hasAttemptedCompact = false;
    private boolean isFallbackModel = false;

    /** 环形缓冲区：最近 LOOP_BUFFER_SIZE 条 (工具名, 参数摘要) */
    private final LinkedList<ToolCallRecord> recentToolCalls = new LinkedList<>();

    // ========== 内部类型 ==========

    private record ToolCallRecord(String toolName, String argsDigest) {}

    // ========== max_tokens 升级 ==========

    /**
     * 返回当前 max_tokens 值。
     */
    public int getCurrentMaxTokens() {
        return currentMaxTokens;
    }

    public int getMaxTokensStage() {
        return maxTokensStage;
    }

    /**
     * 尝试升级 max_tokens 到下一级。
     * @return true 如果升级成功，false 如果已达最高级
     */
    public boolean escalateMaxTokens() {
        if (maxTokensStage >= MAX_TOKENS_STAGES.length - 1) {
            log.warn("max_tokens 已达最高级 {}", currentMaxTokens);
            return false;
        }
        maxTokensStage++;
        currentMaxTokens = MAX_TOKENS_STAGES[maxTokensStage];
        log.info("max_tokens 升级: stage={}, value={}", maxTokensStage, currentMaxTokens);
        return true;
    }

    /**
     * max_tokens 是否已达最高级。
     */
    public boolean isMaxTokensExhausted() {
        return maxTokensStage >= MAX_TOKENS_STAGES.length - 1;
    }

    // ========== 续写 ==========

    /**
     * 记录一次续写请求。
     * @return true 如果仍在额度内，false 如果已达上限
     */
    public boolean recordContinuation() {
        continuationCount++;
        if (continuationCount > MAX_CONTINUATIONS) {
            log.warn("续写次数已达上限 {}", MAX_CONTINUATIONS);
            return false;
        }
        log.info("续写 {}/{}", continuationCount, MAX_CONTINUATIONS);
        return true;
    }

    /**
     * 续写是否已达上限。
     */
    public boolean isContinuationExhausted() {
        return continuationCount > MAX_CONTINUATIONS;
    }

    // ========== 529 / fallback ==========

    /**
     * 记录一次 529 错误。
     * @return true 如果达到 fallback 切换阈值
     */
    public boolean record529() {
        consecutive529++;
        if (consecutive529 >= FALLBACK_AFTER_CONSECUTIVE_529) {
            log.warn("连续 {} 次 529，需要 fallback 模型", consecutive529);
            return true;
        }
        return false;
    }

    /**
     * 重置 529 计数（成功调用后调用）
     */
    public void reset529Count() {
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

    // ========== Reactive Compact ==========

    public boolean hasAttemptedCompact() {
        return hasAttemptedCompact;
    }

    public void setAttemptedCompact(boolean v) {
        this.hasAttemptedCompact = v;
    }

    // ========== 循环检测 ==========

    /**
     * 记录一次工具调用，检测循环模式。
     * @return ContinueReason.LOOP_DETECTED 如果检测到循环，否则 null
     */
    public ContinueReason recordToolCall(String toolName, Map<String, Object> args) {
        String digest = digestArgs(toolName, args);
        recentToolCalls.addLast(new ToolCallRecord(toolName, digest));
        if (recentToolCalls.size() > LOOP_BUFFER_SIZE) {
            recentToolCalls.removeFirst();
        }
        return detectLoop(toolName);
    }

    /**
     * 获取工具的循环检测阈值。
     * 按工具类型差异化设置，参考 GitHub 最佳实践。
     */
    private int getThresholdForTool(String toolName) {
        return switch (toolName) {
            case "Read", "Write", "Edit" -> 3;      // 文件操作：3次
            case "Glob", "Grep" -> 5;                // 搜索工具：5次（可能需要多次搜索）
            case "Bash" -> 4;                         // 命令执行：4次
            case "Task" -> 3;                         // 子任务委派：3次
            default -> DEFAULT_LOOP_THRESHOLD;        // 其他：默认3次
        };
    }

    /**
     * 从参数中提取关键信息用于循环检测。
     * 对 Read/Glob 等工具只保留路径部分，忽略其他参数。
     */
    private String digestArgs(String toolName, Map<String, Object> args) {
        if (args == null) return toolName + ":null";
        return switch (toolName) {
            case "Read", "Write", "Edit" -> {
                String path = (String) args.get("path");
                yield toolName + ":" + (path != null ? path : "?");
            }
            case "Glob" -> {
                String pattern = (String) args.get("pattern");
                String path = (String) args.get("path");
                yield toolName + ":" + (pattern != null ? pattern : "?")
                    + "@" + (path != null ? path : ".");
            }
            case "Grep" -> {
                String pattern = (String) args.get("pattern");
                String gpath = (String) args.get("path");
                yield toolName + ":" + (pattern != null ? pattern : "?")
                    + "@" + (gpath != null ? gpath : ".");
            }
            case "Bash" -> {
                String cmd = (String) args.get("command");
                yield toolName + ":" + (cmd != null ? cmd.substring(0, Math.min(cmd.length(), 60)) : "?");
            }
            case "Task" -> {
                String instruction = (String) args.get("instruction");
                yield toolName + ":" + (instruction != null ? instruction.substring(0, Math.min(instruction.length(), 60)) : "?");
            }
            default -> toolName;
        };
    }

    /**
     * 检测是否有连续的工具调用循环。
     * 如果同一工具+参数摘要连续出现 >= 工具阈值，判定为循环。
     */
    private ContinueReason detectLoop(String toolName) {
        int threshold = getThresholdForTool(toolName);
        
        if (recentToolCalls.size() < threshold) return null;

        // 检查最后 N 个调用是否完全相同（连续重复）
        String lastKey = null;
        int consecutiveCount = 0;
        
        for (ToolCallRecord r : recentToolCalls) {
            String currentKey = r.toolName + ":" + r.argsDigest;
            if (currentKey.equals(lastKey)) {
                consecutiveCount++;
                if (consecutiveCount >= threshold) {
                    log.warn("检测到连续循环模式: '{}' 连续出现 {} 次（阈值 {}）",
                        currentKey, consecutiveCount + 1, threshold);
                    return ContinueReason.LOOP_DETECTED;
                }
            } else {
                lastKey = currentKey;
                consecutiveCount = 1;
            }
        }
        return null;
    }

    // ========== 重置 ==========

    /**
     * 成功执行一轮后调用，重置部分状态。
     */
    public void onSuccessfulTurn() {
        reset529Count();
        // 不清空 recentToolCalls — 让循环检测持续跨轮工作
    }

    /**
     * 清空循环检测缓冲区（用于 LOOP_DETECTED 后避免立即再次触发）。
     */
    public void clearRecentToolCalls() {
        recentToolCalls.clear();
    }

    /**
     * 完全重置所有状态（新请求开始时调用）。
     */
    public void reset() {
        maxTokensStage = 0;
        currentMaxTokens = DEFAULT_MAX_TOKENS;
        continuationCount = 0;
        consecutive529 = 0;
        hasAttemptedCompact = false;
        isFallbackModel = false;
        recentToolCalls.clear();
    }
}
