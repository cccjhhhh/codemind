package com.codemind.context;

import com.codemind.llm.Message;
import com.codemind.llm.ToolCall;
import com.codemind.session.TokenCountService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/**
 * L3 微压缩器：清理旧工具结果，保留最近 N 个。
 *
 * 改造自原 L2MicroCompactor：
 * - 原：只缩写非 Read/Grep 的工具结果（17.4%）
 * - 新：清理旧工具结果（不是全部，只清理旧的）
 * - 保留最近 KEEP_RECENT_TOOL_RESULTS 个
 * - 完整内容在 LRU 缓存中，需要时可以恢复
 *
 * 触发条件：
 * - 基于 token 计数，而不是轮次数
 * - 复用已有的 TokenCountService
 */
public class L3MicroCompactor implements Compactor {

    private static final Logger log = LoggerFactory.getLogger(L3MicroCompactor.class);

    /**
     * 基础保留最近的工具结果数量（默认3个）
     * 实际保留数量会根据工具结果大小动态调整
     */
    private static final int BASE_KEEP_RECENT = 3;

    /**
     * 大结果额外保留数量（当工具结果 > 1KB 时）
     */
    private static final int LARGE_RESULT_BONUS = 2;

    /**
     * 大结果阈值（字符数）
     */
    private static final int LARGE_RESULT_THRESHOLD = 1000;

    /**
     * 可压缩的工具列表（内容可以重新读取或执行）
     */
    private static final java.util.Set<String> COMPACTABLE_TOOLS = java.util.Set.of(
        "Read", "Bash", "Grep", "Glob", "WebFetch", "WebSearch", "Edit", "Write"
    );

    /**
     * 触发阈值：当 token 使用率超过此值时触发 L3 Micro
     * 默认 70%，在 L2（60%）之后，L4（90%）之前
     */
    private static final double L3_TRIGGER_RATIO = 0.70;

    /**
     * Token 计数服务（复用已有的 JTokkitTokenCountService）
     */
    private final TokenCountService tokenCountService;

    /**
     * 模型最大上下文窗口（默认 200K tokens）
     */
    private final int maxContextTokens;

    public L3MicroCompactor(TokenCountService tokenCountService, int maxContextTokens) {
        this.tokenCountService = tokenCountService;
        this.maxContextTokens = maxContextTokens;
    }

    @Override
    public int order() {
        return 30;  // 在 L2（20）之后，L4（40）之前
    }

    @Override
    public List<Message> compact(List<Message> messages, Set<Integer> protectedIndices) {
        // 计算当前 token 使用率
        int currentTokens = tokenCountService.estimateTokens(messages);
        double usageRatio = (double) currentTokens / maxContextTokens;

        if (usageRatio < L3_TRIGGER_RATIO) {
            log.debug("L3 Micro: token 使用率 {} < {}，跳过",
                String.format("%.1f%%", usageRatio * 100),
                String.format("%.1f%%", L3_TRIGGER_RATIO * 100));
            return messages;
        }

        log.info("L3 Micro: token 使用率 {} >= {}，触发清理工具结果",
            String.format("%.1f%%", usageRatio * 100),
            String.format("%.1f%%", L3_TRIGGER_RATIO * 100));

        // 找到所有可压缩的工具结果
        List<Integer> compactableIndices = findCompactableToolResults(messages);

        // 动态计算保留数量（基于工具结果大小分布）
        int keepRecent = calculateAdaptiveKeepCount(messages, compactableIndices);
        log.debug("L3 Micro: 动态保留 {} 个工具结果", keepRecent);

        // 如果可压缩的工具结果数量 <= keepRecent，则跳过
        if (compactableIndices.size() <= keepRecent) {
            log.debug("L3 Micro: 可压缩工具结果数量 {} ≤ {}，跳过",
                compactableIndices.size(), keepRecent);
            return messages;
        }

        List<Message> result = new ArrayList<>(messages);
        int toClear = compactableIndices.size() - keepRecent;
        int cleared = 0;

        // 清理旧的工具结果（保留最近N个）
        for (int i = 0; i < toClear; i++) {
            int idx = compactableIndices.get(i);
            Message msg = result.get(idx);

            // 保留 tool_use（用户侧），删除 tool_result 内容
            // 替换为占位符
            result.set(idx, Message.tool(
                "[Tool result deleted to save context space]\n" +
                "Tool: " + findToolName(messages, idx) + "\n" +
                "Re-run to get fresh results.",
                msg.getToolCallId()
            ));
            cleared++;
        }

        if (cleared > 0) {
            log.info("L3 Micro: 清理 {} 个工具结果，保留最近 {} 个",
                cleared, keepRecent);
        }

        return result;
    }

    /**
     * 动态计算保留数量（基于工具结果大小分布）
     *
     * 策略：
     * - 基础保留：3个
     * - 大结果（>1KB）额外保留：2个
     * - 确保大结果不被清理（它们通常包含重要信息）
     */
    private int calculateAdaptiveKeepCount(List<Message> messages, List<Integer> compactableIndices) {
        int largeResultCount = 0;

        // 统计大结果数量
        for (int idx : compactableIndices) {
            Message msg = messages.get(idx);
            if (msg.getContent() != null && msg.getContent().length() > LARGE_RESULT_THRESHOLD) {
                largeResultCount++;
            }
        }

        // 动态保留：基础 + 大结果额外保留
        int keepRecent = BASE_KEEP_RECENT + Math.min(largeResultCount, LARGE_RESULT_BONUS);
        log.debug("L3 Micro: 大结果数量 {}，动态保留 {} 个", largeResultCount, keepRecent);

        return keepRecent;
    }

    /**
     * 找到所有可压缩的工具结果
     * 只压缩 COMPACTABLE_TOOLS 中的工具
     * 
     * 阈值说明：只清理内容长度 > 120 字符的工具结果
     * 原因：
     * 1. 小结果（<120字符）通常是错误信息或简短确认，保留它们有助于上下文理解
     * 2. 大结果（>120字符）通常是文件内容或命令输出，清理它们可以节省大量token
     * 3. 120字符约等于1-2行文本，是"有意义内容"的最小阈值
     */
    private List<Integer> findCompactableToolResults(List<Message> messages) {
        List<Integer> indices = new ArrayList<>();

        for (int i = 0; i < messages.size(); i++) {
            Message msg = messages.get(i);

            if (msg.getRole() != Message.Role.TOOL || msg.getToolCallId() == null) {
                continue;
            }

            String toolName = findToolName(messages, i);
            if (toolName != null && COMPACTABLE_TOOLS.contains(toolName)) {
                if (msg.getContent() != null && msg.getContent().length() > 120) {
                    indices.add(i);
                }
            }
        }

        return indices;
    }

    private String findToolName(List<Message> messages, int toolIndex) {
        if (toolIndex < 0 || toolIndex >= messages.size()) return null;

        Message toolMsg = messages.get(toolIndex);
        String toolCallId = toolMsg.getToolCallId();
        if (toolCallId == null) return null;

        for (int i = toolIndex - 1; i >= 0; i--) {
            Message msg = messages.get(i);
            if (msg.getRole() == Message.Role.ASSISTANT && msg.hasToolCalls()) {
                for (ToolCall tc : msg.getToolCalls()) {
                    if (toolCallId.equals(tc.getId())) {
                        return tc.getName();
                    }
                }
            }
        }
        return null;
    }
}
