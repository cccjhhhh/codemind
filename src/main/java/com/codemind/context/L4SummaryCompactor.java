package com.codemind.context;

import com.codemind.llm.LLMClient;
import com.codemind.llm.LLMResponse;
import com.codemind.llm.Message;
import com.codemind.llm.ToolCall;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/**
 * L4 压缩器 — LLM 对话摘要。
 *
 * <p>与前三级不同，L4 不是机械裁切，而是调用 LLM 对整个对话做语义摘要，
 * 返回单条 Message 替换全部历史。</p>
 *
 * <p>order=40，不参与常规 {@link ContextCompressionOrchestrator#run} 管线，
 * 由 {@link ContextCompressionOrchestrator#summarize} 独立触发。</p>
 */
public class L4SummaryCompactor implements Compactor {

    private static final Logger log = LoggerFactory.getLogger(L4SummaryCompactor.class);

    private static final int MAX_CONVERSATION_CHARS = 500_000;

    private final LLMClient llmClient;

    public L4SummaryCompactor(LLMClient llmClient) {
        this.llmClient = llmClient;
    }

    @Override
    public int order() {
        return 40;
    }

    /**
     * Compactor 接口实现：从消息列表中提取 Read 文件内容，调用 LLM 做摘要，
     * 返回单条 {@code [Message.user(summary)]}。
     *
     * <p>此方法不包含 fileContentCache（LRU 缓存），
     * 如需包含缓存内容请使用 {@link ContextCompressionOrchestrator#summarize}。</p>
     */
    @Override
    public List<Message> compact(List<Message> messages, Set<Integer> protectedReadIndices) {
        if (messages == null || messages.isEmpty()) {
            return messages;
        }

        String conversation = truncateConversation(messages);
        String readFiles = extractReadFilesSection(messages, protectedReadIndices);
        String summary = callLlmSummary(conversation, readFiles, "");

        if (summary == null) {
            return messages;
        }

        // 保留最近 3 轮完整对话
        List<int[]> bounds = ContextCompressionOrchestrator.findRoundBounds(messages);
        List<Message> recent = new ArrayList<>();
        for (int r = Math.max(0, bounds.size() - 3); r < bounds.size(); r++) {
            int[] b = bounds.get(r);
            for (int i = b[0]; i <= b[1]; i++) {
                recent.add(messages.get(i));
            }
        }

        List<Message> result = new ArrayList<>();
        result.add(Message.user("[Compacted]\n\n" + summary));
        result.addAll(recent);
        return result;
    }

    // ==================== 核心摘要方法（供 Orchestrator.summarize 调用） ====================

    /**
     * 核心摘要方法 — 直接传入已提取好的内容，跳过消息遍历。
     * 比 {@link #compact} 多支持 fileContentCache 注入。
     */
    String callLlmSummary(String conversation, String readFilesSection, String cachedFilesSection) {
        String prompt = buildPrompt(conversation, readFilesSection, cachedFilesSection);
        if (prompt == null) return null;

        try {
            LLMResponse response = llmClient.chat(List.of(
                Message.system("You are a conversation summarizer."),
                Message.user(prompt)
            ), 65536);

            if (response.getContent() == null || response.getContent().isBlank()) {
                log.warn("L4 摘要返回空");
                return null;
            }

            return response.getContent();

        } catch (Exception e) {
            log.error("L4 摘要失败: {}", e.getMessage());
            return null;
        }
    }

    // ==================== 摘要 prompt 构建 ====================

    private static String buildPrompt(String conversation, String readFiles, String cachedFiles) {
        if (conversation == null || conversation.isBlank()) return null;

        return "Summarize this coding-agent conversation so work can continue.\n"
            + "Preserve:\n"
            + "1. Current goal / 当前目标\n"
            + "2. Key findings and decisions / 关键发现与决策\n"
            + "3. Files read and their KEY CONTENTS — include ACTUAL SOURCE CODE for each file read "
            + "(function signatures, class declarations, method bodies, important logic) / "
            + "已读文件及其关键内容 — 必须包含实际源代码\n"
            + "4. Files changed and what was modified / 已改文件及修改内容\n"
            + "5. Remaining work / 剩余工作\n"
            + "6. User constraints and preferences / 用户约束\n\n"
            + "IMPORTANT: Your summary MUST include actual source code details for ALL files that were read. "
            + "The agent must be able to continue work WITHOUT re-reading any files. "
            + "Include file paths, class/interface declarations, method signatures, and key logic.\n\n"
            + "At the end of this prompt you will find a section called 'Read Files' or 'Cached File Contents'. "
            + "INCLUDE ALL file contents from that section verbatim in your summary.\n\n"
            + "Conversation:\n" + conversation
            + readFiles
            + cachedFiles;
    }

    // ==================== 从消息中提取 Read 文件内容 ====================

    static String extractReadFilesSection(List<Message> messages, Set<Integer> protectedReadIndices) {
        if (protectedReadIndices == null || protectedReadIndices.isEmpty()) return "";

        StringBuilder sb = new StringBuilder();
        sb.append("\n\n=== Read Files (full contents that must be preserved) ===\n");
        int readCount = 0;

        for (int i = 0; i < messages.size(); i++) {
            if (!protectedReadIndices.contains(i)) continue;
            Message msg = messages.get(i);
            if (msg.getRole() == Message.Role.TOOL && msg.getToolCallId() != null) {
                String path = findReadPath(messages, i);
                sb.append("\n--- ").append(path).append(" ---\n");
                sb.append(msg.getContent()).append("\n");
                readCount++;
            }
        }

        if (readCount == 0) {
            return "";
        }
        return sb.toString();
    }

    // ==================== 辅助方法 ====================

    private static String truncateConversation(List<Message> messages) {
        String conversation = messages.toString();
        if (conversation.length() > MAX_CONVERSATION_CHARS) {
            conversation = conversation.substring(conversation.length() - MAX_CONVERSATION_CHARS);
        }
        return conversation;
    }

    private static String findReadPath(List<Message> messages, int toolIndex) {
        // 先用共享方法验证这是 Read 工具
        String toolName = ContextCompressionOrchestrator.findToolName(messages, toolIndex);
        if (toolName == null) return "?";
        // 提取 path 参数
        Message toolMsg = messages.get(toolIndex);
        String toolCallId = toolMsg.getToolCallId();
        if (toolCallId == null) return "?";
        for (int i = toolIndex - 1; i >= 0; i--) {
            Message msg = messages.get(i);
            if (msg.getRole() == Message.Role.ASSISTANT && msg.hasToolCalls()) {
                for (ToolCall tc : msg.getToolCalls()) {
                    if (toolCallId.equals(tc.getId())) {
                        Object path = tc.getArguments() != null ? tc.getArguments().get("path") : null;
                        return path != null ? path.toString() : "?";
                    }
                }
            }
        }
        return "?";
    }
}
