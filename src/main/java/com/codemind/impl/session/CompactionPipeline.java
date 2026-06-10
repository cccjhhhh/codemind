package com.codemind.impl.session;

import com.codemind.api.llm.Message;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;

public class CompactionPipeline {

    private static final Logger log = LoggerFactory.getLogger(CompactionPipeline.class);

    private final int maxMessagesBeforeSnip;
    private final int keepRecentToolResults;
    private final int budgetMaxBytes;
    private final Path spillDir;
    private final int spillThresholdChars;
    private final String sessionId;
    private final boolean saveTranscripts;
    private int consecutiveFailures = 0;

    public CompactionPipeline(int maxMessagesBeforeSnip, int keepRecentToolResults,
                              int budgetMaxBytes, Path spillDir, int spillThresholdChars,
                              String sessionId, boolean saveTranscripts) {
        this.maxMessagesBeforeSnip = maxMessagesBeforeSnip;
        this.keepRecentToolResults = keepRecentToolResults;
        this.budgetMaxBytes = budgetMaxBytes;
        this.spillDir = spillDir;
        this.spillThresholdChars = spillThresholdChars;
        this.sessionId = sessionId;
        this.saveTranscripts = saveTranscripts;
    }

    public int getConsecutiveFailures() { return consecutiveFailures; }
    public void resetFailures() { consecutiveFailures = 0; }

    /**
     * 运行完整管线: L3 → L1 → L2 → (可选 L4)
     * @return 处理后的消息列表
     */
    public List<Message> run(List<Message> messages, Runnable l4Compactor) {
        List<Message> result = new ArrayList<>(messages);

        // L3: 大结果落盘（先跑，给 L1/L2 的占位留原始内容）
        result = toolResultBudget(result);

        // L1: 裁剪中间旧消息
        result = snipCompact(result);

        // L2: 旧工具结果占位
        result = microCompact(result);

        // 可选 L4（由外部传入避免依赖循环）
        if (l4Compactor != null) {
            l4Compactor.run();
        }

        return result;
    }

    // === L3: 大结果落盘 ===
    List<Message> toolResultBudget(List<Message> messages) {
        List<Message> result = new ArrayList<>(messages);
        boolean changed = false;

        for (int i = 0; i < result.size(); i++) {
            Message msg = result.get(i);
            if (msg.getRole() == Message.Role.TOOL && msg.getContent() != null
                    && msg.getContent().length() > spillThresholdChars) {
                String persistedPath = persistToSpill(msg.getContent(), "tool_result");
                String preview = msg.getContent().substring(0, Math.min(200, msg.getContent().length()));
                result.set(i, Message.tool(
                    "[结果已保存](" + persistedPath + ")\n预览: " + preview,
                    msg.getToolCallId()
                ));
                changed = true;
            }
        }

        if (changed) log.debug("L3: 已落盘大工具结果");
        return result;
    }

    private String persistToSpill(String content, String prefix) {
        try {
            String timestamp = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss"));
            Path sessionSpillDir = spillDir.resolve(sessionId);
            Files.createDirectories(sessionSpillDir);
            Path file = sessionSpillDir.resolve(timestamp + "-" + prefix + ".md");
            Files.writeString(file, content);
            return file.toString();
        } catch (IOException e) {
            log.warn("落盘失败: {}", e.getMessage());
            return "(persist failed)";
        }
    }

    // === L1: 裁剪中间旧消息 ===
    List<Message> snipCompact(List<Message> messages) {
        if (messages.size() <= maxMessagesBeforeSnip) return messages;

        int keepHead = 3;
        int keepTail = maxMessagesBeforeSnip - 3;
        int headEnd = keepHead;
        int tailStart = messages.size() - keepTail;

        // 边界保护：tool_use 与其 tool_result 不拆分
        while (headEnd > 0 && headEnd < messages.size()
                && messages.get(headEnd).getRole() == Message.Role.TOOL) {
            headEnd++;
        }
        while (tailStart > 0 && tailStart < messages.size()
                && messages.get(tailStart).getRole() == Message.Role.TOOL) {
            tailStart--;
        }
        // 如果 tool 配对越过了裁剪边界，回退
        if (headEnd > 0 && hasToolCall(messages.get(headEnd - 1))) {
            while (headEnd < messages.size() && messages.get(headEnd).getRole() == Message.Role.TOOL) {
                headEnd++;
            }
        }
        if (tailStart > 0 && tailStart < messages.size()
                && messages.get(tailStart).getRole() == Message.Role.TOOL
                && hasToolCall(messages.get(tailStart - 1))) {
            tailStart--;
        }

        if (headEnd >= tailStart) return messages;

        int snipped = tailStart - headEnd;
        List<Message> result = new ArrayList<>(messages.subList(0, headEnd));
        result.add(Message.user("[snipped " + snipped + " messages]"));
        result.addAll(messages.subList(tailStart, messages.size()));

        log.debug("L1: 裁剪 {} 条消息", snipped);
        return result;
    }

    private boolean hasToolCall(Message msg) {
        return msg.getRole() == Message.Role.ASSISTANT && msg.hasToolCalls();
    }

    // === L2: 旧结果占位 ===
    List<Message> microCompact(List<Message> messages) {
        List<Message> result = new ArrayList<>(messages);
        int toolCount = 0;

        for (int i = result.size() - 1; i >= 0; i--) {
            Message msg = result.get(i);
            if (msg.getRole() == Message.Role.TOOL) {
                toolCount++;
                if (toolCount > keepRecentToolResults && msg.getContent() != null
                        && msg.getContent().length() > 120) {
                    result.set(i, Message.tool(
                        "[Earlier tool result compacted. Re-run if needed.]",
                        msg.getToolCallId()
                    ));
                }
            }
        }

        return result;
    }
}
