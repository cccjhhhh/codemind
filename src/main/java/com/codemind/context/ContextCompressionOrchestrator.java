package com.codemind.context;

import com.codemind.agent.engine.TokenBudget;
import com.codemind.llm.LLMClient;
import com.codemind.llm.Message;
import com.codemind.session.SessionContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;

/**
 * 上下文压缩编排器 —— 压缩管线的唯一入口。
 *
 * 【harness 规则 03-compression-single-entry】
 * 所有压缩必须经过此类。
 *
 * 执行流程：
 * 1. 双触发检查 (round > compressOnRounds OR token > compactOnRatio)
 * 2. 一次扫描计算受保护索引（Read + Grep）
 * 3. 按 order() 依次执行 L3 → L1 → L2
 * 4. 每次压缩后重算受保护索引
 * 5. L4 LLM 摘要通过 {@link #summarize} 独立触发
 * 6. 返回 CompressionResult
 */
public class ContextCompressionOrchestrator {

    private static final Logger log = LoggerFactory.getLogger(ContextCompressionOrchestrator.class);

    private final List<Compactor> compactors;
    private final L4SummaryCompactor l4SummaryCompactor;
    private final long maxExecutionTimeMs;
    private final int compressOnRounds;
    private final double compactOnRatio;        // token 百分比触发阈值（0.70 = 70%）
    private final TokenBudget tokenBudget;      // 用于百分比检查（可为 null）
    private int consecutiveFailures = 0;

    public ContextCompressionOrchestrator(List<Compactor> compactors, L4SummaryCompactor l4SummaryCompactor,
                                           long maxExecutionTimeMs, int compressOnRounds,
                                           double compactOnRatio, TokenBudget tokenBudget) {
        this.compactors = compactors.stream()
                .sorted(Comparator.comparingInt(Compactor::order))
                .toList();
        this.l4SummaryCompactor = l4SummaryCompactor;
        this.maxExecutionTimeMs = maxExecutionTimeMs;
        this.compressOnRounds = compressOnRounds;
        this.compactOnRatio = compactOnRatio;
        this.tokenBudget = tokenBudget;
    }

    /**
     * 创建默认编排器，包含 L1–L4 压缩器。
     *
     * @param l1MaxRounds           L1 最多砍轮数
     * @param l2MaxCompactions       L2 最多缩写数
     * @param l2KeepRecentRounds     L2 保留最近轮数
     * @param budgetMaxBytes         L3 预算（当前未使用，保留签名兼容性）
     * @param spillDir               L3 持久化目录
     * @param spillThresholdChars    L3 阈值
     * @param sessionId              会话标识
     * @param saveTranscripts        是否保存 transcripts
     * @param llmClient              LLM 客户端（用于 L4 摘要）
     * @param maxExecutionTimeSeconds 最大执行时间（秒），用于 L4 摘要超时检查
     * @param compressOnRounds       轮次触发阈值
     * @param compactOnRatio         token 百分比触发阈值（0.70 = 70%）
     * @param tokenBudget            TokenBudget 实例，用于百分比检查
     */
    public static ContextCompressionOrchestrator createDefault(
            int l1MaxRounds,
            int l2MaxCompactions,
            int l2KeepRecentRounds,
            int budgetMaxBytes,
            Path spillDir,
            int spillThresholdChars,
            String sessionId,
            boolean saveTranscripts,
            LLMClient llmClient,
            int maxExecutionTimeSeconds,
            int compressOnRounds,
            double compactOnRatio,
            TokenBudget tokenBudget) {
        List<Compactor> compactors = new ArrayList<>();
        compactors.add(new L3SpillCompactor(spillThresholdChars, spillDir, sessionId));
        compactors.add(new L1SnipCompactor(l1MaxRounds));
        compactors.add(new L2MicroCompactor(l2MaxCompactions, l2KeepRecentRounds));
        // L4 不加入常规管线，由 summarize() 单独触发
        L4SummaryCompactor l4 = new L4SummaryCompactor(llmClient);
        long maxExecMs = maxExecutionTimeSeconds > 0 ? maxExecutionTimeSeconds * 1000L : 0;
        return new ContextCompressionOrchestrator(compactors, l4, maxExecMs, compressOnRounds,
                compactOnRatio, tokenBudget);
    }

    /**
     * 重置连续失败计数。
     */
    public void resetFailures() {
        consecutiveFailures = 0;
    }

    // ==================== L4 摘要 ====================

    /**
     * L4 全量 LLM 摘要 — 供 CompactHandler/LoopBreakHandler/ThinkHandler 调用。
     *
     * @param context          会话上下文（用于获取消息历史）
     * @param startTime        请求开始时间（毫秒，用于超时检查）
     * @param fileContentCache 文件内容 LRU 缓存（可空）
     * @return 摘要文本，失败返回 null
     */
    public String summarize(SessionContext context, long startTime, Map<String, String> fileContentCache) {
        List<Message> messages = context.getManagedHistory();
        if (messages == null || messages.isEmpty()) return null;

        // 保存 transcript
        try {
            Path transcriptDir = Path.of(".codemind", "transcripts");
            Files.createDirectories(transcriptDir);
            String ts = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss"));
            Path transcriptPath = transcriptDir.resolve(ts + ".jsonl");
            Files.writeString(transcriptPath, messages.toString());
        } catch (Exception e) {
            log.warn("保存 transcript 失败: {}", e.getMessage());
        }

        // 超时检查（至少留 10 秒）
        long elapsed = System.currentTimeMillis() - startTime;
        if (maxExecutionTimeMs > 0 && elapsed > maxExecutionTimeMs - 10_000) {
            log.warn("执行时间不足，跳过 L4 摘要");
            return null;
        }

        // 提取 conversation 字符串
        String conversation = messages.toString();
        if (conversation.length() > 500_000) {
            conversation = conversation.substring(conversation.length() - 500_000);
        }

        // 计算受保护的 Read 结果索引 + 提取文件内容
        Set<Integer> protectedIndices = findReadResultIndices(messages);
        String readFiles = L4SummaryCompactor.extractReadFilesSection(messages, protectedIndices);

        // 构建缓存文件内容
        String cachedFiles = getCachedFileContentForPrompt(fileContentCache);

        // 委托 L4SummaryCompactor 做 LLM 摘要
        return l4SummaryCompactor.callLlmSummary(conversation, readFiles, cachedFiles);
    }

    private static String getCachedFileContentForPrompt(Map<String, String> fileContentCache) {
        if (fileContentCache == null || fileContentCache.isEmpty()) return "";
        StringBuilder sb = new StringBuilder();
        sb.append("\n\n=== Read Files (guaranteed full content, do not omit) ===\n");
        synchronized (fileContentCache) {
            for (Map.Entry<String, String> entry : fileContentCache.entrySet()) {
                sb.append("\n--- ").append(entry.getKey()).append(" ---\n");
                sb.append(entry.getValue()).append("\n");
            }
        }
        return sb.toString();
    }

    // ==================== L1-L3 管线 ====================

    /**
     * 执行 L1-L3 压缩管线。
     *
     * <p>双触发检查：roundCount > compressOnRounds OR token 使用率 > compactOnRatio。
     * 只有至少一个条件满足时才执行压缩。
     * 每次压缩后重算受保护索引，避免索引偏移。</p>
     *
     * @param messages      原始消息列表（含 system message）
     * @param systemMessage 系统消息引用（当前未使用，保留签名兼容性）
     * @return 压缩结果
     */
    public CompressionResult run(List<Message> messages, Message systemMessage) {
        // 双触发检查
        int roundCount = countRounds(messages);
        boolean roundTrigger = roundCount > compressOnRounds;
        boolean tokenTrigger = false;
        if (tokenBudget != null) {
            double usage = tokenBudget.getUsageRatio(messages);
            tokenTrigger = usage > compactOnRatio;
            if (tokenTrigger) {
                log.info("Token 百分比触发: {}% > {}% (rounds={}, triggerRounds={})",
                        String.format("%.1f", usage * 100),
                        String.format("%.0f", compactOnRatio * 100),
                        roundCount, compressOnRounds);
            }
        }
        if (!roundTrigger && !tokenTrigger) {
            return new CompressionResult(messages, false, false, messages.size(), messages.size());
        }
        if (roundTrigger) {
            log.info("轮次触发: {} > {} (token 使用率={})",
                    roundCount, compressOnRounds,
                    tokenBudget != null ? String.format("%.1f%%", tokenBudget.getUsageRatio(messages) * 100) : "N/A");
        }

        int originalSize = messages.size();
        List<Message> result = new ArrayList<>(messages);
        boolean didCompact = false;

        // 1. 一次扫描：保护 Read + Grep 工具结果
        Set<Integer> protectedIndices = findProtectedToolResultIndices(result);

        // 2. 依次执行 L3 → L1 → L2（L4 不在此管线中）
        for (Compactor compactor : compactors) {
            if (compactor.order() >= 40) continue;
            List<Message> before = result;
            result = compactor.compact(result, protectedIndices);
            if (result.size() < before.size()) {
                didCompact = true;
                log.debug("Compactor {}: {} → {} 条消息", compactor.name(), before.size(), result.size());
            }
            // 重算保护索引，防止前序压缩的删除操作导致索引偏移
            protectedIndices = findProtectedToolResultIndices(result);
        }

        return new CompressionResult(result, didCompact, false, originalSize, result.size());
    }

    /**
     * 运行完整压缩管线并返回压缩后的消息列表（便捷方法）。
     */
    public List<Message> run(List<Message> messages) {
        var result = run(messages, null);

        if (!result.didCompact()) {
            consecutiveFailures++;
        } else {
            consecutiveFailures = 0;
        }

        return result.compressedMessages();
    }

    // ==================== 公共静态工具方法 ====================

    /**
     * 计算消息列表中的 ReAct 步骤数量。
     * 每个步骤 = ASSISTANT(含工具调用) + 其后的 TOOL 结果。
     * 这与用户轮次不同 —— 一个用户提问可能包含多个 ReAct 步骤。
     */
    public static int countRounds(List<Message> messages) {
        return findRoundBounds(messages).size();
    }

    /**
     * 计算消息列表中 ReAct 步骤的边界。
     *
     * codemind 的 ReAct 模式：
     *   USER → [ASSISTANT(with tools) + TOOLs]×N → [ASSISTANT(final)]
     *
     * 一个"步骤" = ASSISTANT + 其后的连续 TOOL 结果。
     * L1/L2 以此为单位进行裁剪和保留，确保不拆散 ASSISTANT+TOOL 配对。
     */
    public static List<int[]> findRoundBounds(List<Message> messages) {
        List<int[]> bounds = new ArrayList<>();
        int i = 0;
        if (!messages.isEmpty() && messages.get(0).getRole() == Message.Role.SYSTEM) {
            i = 1;
        }
        while (i < messages.size()) {
            if (messages.get(i).getRole() != Message.Role.ASSISTANT) {
                i++;
                continue;
            }
            int stepStart = i;
            i++;
            while (i < messages.size() && messages.get(i).getRole() == Message.Role.TOOL) {
                i++;
            }
            bounds.add(new int[]{stepStart, i - 1});
        }
        return bounds;
    }

    // ==================== 受保护索引扫描 ====================

    /**
     * 找出所有 Read 工具的结果消息索引（仅 Read，用于 L4 摘要提取）。
     */
    public static Set<Integer> findReadResultIndices(List<Message> messages) {
        Set<String> toolCallIds = new HashSet<>();
        for (Message msg : messages) {
            if (msg.getRole() == Message.Role.ASSISTANT && msg.hasToolCalls()) {
                for (var tc : msg.getToolCalls()) {
                    if ("Read".equals(tc.getName())) {
                        toolCallIds.add(tc.getId());
                    }
                }
            }
        }
        Set<Integer> indices = new HashSet<>();
        for (int i = 0; i < messages.size(); i++) {
            Message msg = messages.get(i);
            if (msg.getRole() == Message.Role.TOOL
                    && msg.getToolCallId() != null
                    && toolCallIds.contains(msg.getToolCallId())) {
                indices.add(i);
            }
        }
        return indices;
    }

    /**
     * 找出所有受保护的工具结果索引（Read + Grep），用于压缩管线保护。
     */
    public static Set<Integer> findProtectedToolResultIndices(List<Message> messages) {
        Set<String> toolCallIds = new HashSet<>();
        for (Message msg : messages) {
            if (msg.getRole() == Message.Role.ASSISTANT && msg.hasToolCalls()) {
                for (var tc : msg.getToolCalls()) {
                    String name = tc.getName();
                    if ("Read".equals(name) || "Grep".equals(name)) {
                        toolCallIds.add(tc.getId());
                    }
                }
            }
        }
        Set<Integer> indices = new HashSet<>();
        for (int i = 0; i < messages.size(); i++) {
            Message msg = messages.get(i);
            if (msg.getRole() == Message.Role.TOOL
                    && msg.getToolCallId() != null
                    && toolCallIds.contains(msg.getToolCallId())) {
                indices.add(i);
            }
        }
        return indices;
    }
}
