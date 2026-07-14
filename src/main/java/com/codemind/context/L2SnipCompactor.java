package com.codemind.context;

import com.codemind.llm.Message;
import com.codemind.llm.ToolCall;
import com.codemind.session.TokenCountService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;

/**
 * L2 截断器：基于 token 计数的智能截断。
 *
 * 保留策略（基于 token 预算，不是固定轮数）：
 * - 保留前 KEEP_HEAD_ROUNDS 轮（初始上下文）
 * - 保留最后约 TAIL_TOKEN_BUDGET tokens（当前工作）
 * - 至少保留 MIN_TAIL_MESSAGES 条消息
 * - 中间全部删除
 *
 * 触发条件：
 * - 基于 token 计数，而不是轮次数
 * - 复用已有的 TokenCountService
 */
public class L2SnipCompactor implements Compactor {

    private static final Logger log = LoggerFactory.getLogger(L2SnipCompactor.class);

    /**
     * 保留前 N 轮（初始上下文）
     */
    private static final int KEEP_HEAD_ROUNDS = 3;

    /**
     * 尾部 token 预算（~20K tokens，约 5000 个词）
     * 生产实践：laia-core、ContextCompressionEngine 都用这个方案
     */
    private static final int TAIL_TOKEN_BUDGET = 20000;

    /**
     * 至少保留的消息数量（硬最小值）
     */
    private static final int MIN_TAIL_MESSAGES = 3;

    /**
     * 触发阈值：当 token 使用率超过此值时触发 L2 Snip
     * 默认 60%，在 L4 之前触发
     */
    private static final double L2_TRIGGER_RATIO = 0.60;

    /**
     * Token 计数服务（复用已有的 JTokkitTokenCountService）
     */
    private final TokenCountService tokenCountService;

    /**
     * 模型最大上下文窗口（默认 200K tokens）
     */
    private final int maxContextTokens;

    public L2SnipCompactor(TokenCountService tokenCountService, int maxContextTokens) {
        this.tokenCountService = tokenCountService;
        this.maxContextTokens = maxContextTokens;
    }

    @Override
    public int order() {
        return 20;  // 在 L1（10）之后，L3（30）之前
    }

    @Override
    public List<Message> compact(List<Message> messages, Set<Integer> protectedIndices) {
        // 计算当前 token 使用率
        int currentTokens = tokenCountService.estimateTokens(messages);
        double usageRatio = (double) currentTokens / maxContextTokens;

        if (usageRatio < L2_TRIGGER_RATIO) {
            log.debug("L2 Snip: token 使用率 {} < {}，跳过",
                String.format("%.1f%%", usageRatio * 100),
                String.format("%.1f%%", L2_TRIGGER_RATIO * 100));
            return messages;
        }

        log.info("L2 Snip: token 使用率 {} >= {}，触发截断",
            String.format("%.1f%%", usageRatio * 100),
            String.format("%.1f%%", L2_TRIGGER_RATIO * 100));

        // 计算轮次边界
        List<int[]> roundBounds = findRoundBounds(messages);

        int headEnd = KEEP_HEAD_ROUNDS;

        // 基于 token 预算计算尾部起始位置
        int tailStart = findTailByTokenBudget(messages, headEnd, TAIL_TOKEN_BUDGET);

        // 确保至少保留 MIN_TAIL_MESSAGES 条消息
        int minTailStart = Math.max(headEnd + 1, messages.size() - MIN_TAIL_MESSAGES);
        tailStart = Math.min(tailStart, minTailStart);

        // 确保 headEnd < tailStart
        if (headEnd >= tailStart) {
            log.debug("L2 Snip: headEnd {} >= tailStart {}，跳过", headEnd, tailStart);
            return messages;
        }

        // 转换为消息索引
        int headMsgIndex = headEnd < roundBounds.size() ? roundBounds.get(headEnd)[0] : messages.size();
        int tailMsgIndex = tailStart < roundBounds.size() ? roundBounds.get(tailStart)[0] : messages.size();

        // 边界保护：确保 tool_use 和 tool_result 不分离
        headMsgIndex = adjustForToolPairs(messages, headMsgIndex, true);
        tailMsgIndex = adjustForToolPairs(messages, tailMsgIndex, false);

        // 中间全部删除
        int snipped = tailMsgIndex - headMsgIndex;
        List<Message> result = new ArrayList<>();
        result.addAll(messages.subList(0, headMsgIndex));
        result.add(Message.assistant("[snipped " + snipped + " messages from conversation middle]"));
        result.addAll(messages.subList(tailMsgIndex, messages.size()));

        log.info("L2 Snip: 删除 {} 条消息（前 {} 轮 + 后约 {} tokens）",
            snipped, headEnd, TAIL_TOKEN_BUDGET);
        return result;
    }

    /**
     * 基于 token 预算计算尾部起始位置
     *
     * 从后往前扫描，累积 token 数，直到达到预算上限。
     */
    private int findTailByTokenBudget(List<Message> messages, int headEnd, int tokenBudget) {
        int accumulated = 0;
        int cutIdx = messages.size();

        for (int i = messages.size() - 1; i >= headEnd; i--) {
            Message msg = messages.get(i);
            int msgTokens = tokenCountService.estimateTokens(msg);

            // 如果加上这条消息会超预算，且已经保留了至少 MIN_TAIL_MESSAGES 条
            if (accumulated + msgTokens > tokenBudget && (messages.size() - i) >= MIN_TAIL_MESSAGES) {
                break;
            }

            accumulated += msgTokens;
            cutIdx = i;
        }

        return cutIdx;
    }

    /**
     * 调整索引以确保 tool_use 和 tool_result 不分离
     */
    private int adjustForToolPairs(List<Message> messages, int index, boolean isHead) {
        if (isHead) {
            if (index > 0 && index <= messages.size()) {
                Message prevMsg = messages.get(index - 1);
                if (hasToolUse(prevMsg)) {
                    while (index < messages.size() && isToolResult(messages.get(index))) {
                        index++;
                    }
                }
            }
        } else {
            if (index > 0 && index < messages.size()) {
                Message currentMsg = messages.get(index);
                Message prevMsg = messages.get(index - 1);
                if (isToolResult(currentMsg) && hasToolUse(prevMsg)) {
                    index--;
                }
            }
        }
        return index;
    }

    /**
     * 计算消息列表中 ReAct 步骤的边界
     */
    static List<int[]> findRoundBounds(List<Message> messages) {
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

    private boolean hasToolUse(Message msg) {
        if (msg.getRole() != Message.Role.ASSISTANT) {
            return false;
        }
        return msg.hasToolCalls() && !msg.getToolCalls().isEmpty();
    }

    private boolean isToolResult(Message msg) {
        return msg.getRole() == Message.Role.TOOL && msg.getToolCallId() != null;
    }
}
