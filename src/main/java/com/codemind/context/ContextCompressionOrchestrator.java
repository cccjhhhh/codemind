package com.codemind.context;

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
 * 1. 一次扫描计算 Read 结果索引（protectedIndices）
 * 2. 按 order() 依次执行 L1 → L2 → L3
 * 3. L4 LLM 摘要通过 {@link #summarize} 独立触发（L4SummaryCompactor 实现 Compactor 接口）
 * 4. 返回 CompressionResult
 */
public class ContextCompressionOrchestrator {

    private static final Logger log = LoggerFactory.getLogger(ContextCompressionOrchestrator.class);

    private final List<Compactor> compactors;
    private final L4SummaryCompactor l4SummaryCompactor;
    private final long maxExecutionTimeMs;
    private int consecutiveFailures = 0;

    public ContextCompressionOrchestrator(List<Compactor> compactors, L4SummaryCompactor l4SummaryCompactor, long maxExecutionTimeMs) {
        // 按 order 排序，保证执行顺序
        this.compactors = compactors.stream()
                .sorted(Comparator.comparingInt(Compactor::order))
                .toList();
        this.l4SummaryCompactor = l4SummaryCompactor;
        this.maxExecutionTimeMs = maxExecutionTimeMs;
    }

    /**
     * 创建默认编排器，包含 L1–L4 压缩器。
     *
     * @param maxMessagesBeforeSnip L1 阈值
     * @param keepRecentToolResults L2 保留数量
     * @param budgetMaxBytes L3 预算（当前未使用，保留签名兼容性）
     * @param spillDir L3 持久化目录
     * @param spillThresholdChars L3 阈值
     * @param sessionId 会话标识
     * @param saveTranscripts 是否保存 transcripts
     * @param llmClient LLM 客户端（用于 L4 摘要）
     * @param maxExecutionTimeSeconds 最大执行时间（秒），用于 L4 摘要超时检查
     */
    public static ContextCompressionOrchestrator createDefault(
            int maxMessagesBeforeSnip,
            int keepRecentToolResults,
            int budgetMaxBytes,
            Path spillDir,
            int spillThresholdChars,
            String sessionId,
            boolean saveTranscripts,
            LLMClient llmClient,
            int maxExecutionTimeSeconds) {
        List<Compactor> compactors = new ArrayList<>();
        compactors.add(new L1SnipCompactor(maxMessagesBeforeSnip));
        compactors.add(new L2MicroCompactor(keepRecentToolResults));
        compactors.add(new L3SpillCompactor(spillThresholdChars, spillDir, sessionId));
        // L4 不加入常规管线，由 summarize() 单独触发，但作为 Compactor 存在
        L4SummaryCompactor l4 = new L4SummaryCompactor(llmClient);
        long maxExecMs = maxExecutionTimeSeconds > 0 ? maxExecutionTimeSeconds * 1000L : 0;
        return new ContextCompressionOrchestrator(compactors, l4, maxExecMs);
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
     * <p>与前三级不同，L4 不参与常规 {@link #run} 管线，由各 Handler 在需要时独立触发。
     * 此方法为 L4 的独立入口，符合"所有压缩经过此类"的规则。</p>
     *
     * @param context         会话上下文（用于获取消息历史）
     * @param startTime       请求开始时间（毫秒，用于超时检查）
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
     * @param messages      原始消息列表（含 system message）
     * @param systemMessage 系统消息引用，用于判断系统消息在列表中的位置
     * @return 压缩结果
     */
    public CompressionResult run(List<Message> messages, Message systemMessage) {
        int originalSize = messages.size();
        List<Message> result = new ArrayList<>(messages);

        // 1. 一次扫描：保护 Read 工具结果
        Set<Integer> protectedIndices = findReadResultIndices(result);

        // 2. 依次执行 L1 → L2 → L3（L4 不在此管线中）
        boolean didCompact = false;
        for (Compactor compactor : compactors) {
            if (compactor.order() >= 40) continue; // L4 有独立入口 summarize()
            List<Message> before = result;
            result = compactor.compact(result, protectedIndices);
            if (result.size() < before.size()) {
                didCompact = true;
                log.debug("Compactor {}: {} → {} 条消息", compactor.name(), before.size(), result.size());
            }
        }

        return new CompressionResult(result, didCompact, false, originalSize, result.size());
    }

    /**
     * 运行完整压缩管线并返回压缩后的消息列表（便捷方法）。
     * 等同于 run(messages, null).compressedMessages()。
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

    /**
     * 一次扫描找出所有 Read 工具的结果消息索引。
     */
    public static Set<Integer> findReadResultIndices(List<Message> messages) {
        Set<String> readToolCallIds = new HashSet<>();
        // 第一遍：收集所有 Read 调用的 ID
        for (Message msg : messages) {
            if (msg.getRole() == Message.Role.ASSISTANT && msg.hasToolCalls()) {
                for (var tc : msg.getToolCalls()) {
                    if ("Read".equals(tc.getName())) {
                        readToolCallIds.add(tc.getId());
                    }
                }
            }
        }
        // 第二遍：标记匹配的 TOOL 结果
        Set<Integer> indices = new HashSet<>();
        for (int i = 0; i < messages.size(); i++) {
            Message msg = messages.get(i);
            if (msg.getRole() == Message.Role.TOOL
                    && msg.getToolCallId() != null
                    && readToolCallIds.contains(msg.getToolCallId())) {
                indices.add(i);
            }
        }
        return indices;
    }
}
