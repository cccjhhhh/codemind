package com.codemind.context;

import com.codemind.llm.Message;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.file.Path;
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
 * 3. 返回 CompressionResult
 */
public class ContextCompressionOrchestrator {

    private static final Logger log = LoggerFactory.getLogger(ContextCompressionOrchestrator.class);

    private final List<Compactor> compactors;
    private int consecutiveFailures = 0;

    public ContextCompressionOrchestrator(List<Compactor> compactors) {
        // 按 order 排序，保证执行顺序
        this.compactors = compactors.stream()
                .sorted(Comparator.comparingInt(Compactor::order))
                .toList();
    }

    /**
     * 创建默认编排器，包含 L1、L2、L3 压缩器。
     * 
     * @param maxMessagesBeforeSnip L1 阈值
     * @param keepRecentToolResults L2 保留数量
     * @param budgetMaxBytes L3 预算（当前未使用，保留签名兼容性）
     * @param spillDir L3 持久化目录
     * @param spillThresholdChars L3 阈值
     * @param sessionId 会话标识
     * @param saveTranscripts 是否保存 transcripts（当前未使用）
     */
    public static ContextCompressionOrchestrator createDefault(
            int maxMessagesBeforeSnip,
            int keepRecentToolResults,
            int budgetMaxBytes,
            Path spillDir,
            int spillThresholdChars,
            String sessionId,
            boolean saveTranscripts) {
        List<Compactor> compactors = new ArrayList<>();
        compactors.add(new L1SnipCompactor(maxMessagesBeforeSnip));
        compactors.add(new L2MicroCompactor(keepRecentToolResults));
        compactors.add(new L3SpillCompactor(spillThresholdChars, spillDir, sessionId));
        return new ContextCompressionOrchestrator(compactors);
    }

    /**
     * 重置连续失败计数。
     */
    public void resetFailures() {
        consecutiveFailures = 0;
    }

    /**
     * 执行完整压缩管线。
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

        // 2. 依次执行 L1 → L2 → L3
        boolean didCompact = false;
        for (Compactor compactor : compactors) {
            if (compactor.order() >= 40) continue; // L4 单独处理
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
