package com.codemind.context;

import com.codemind.llm.Message;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * L2 微压缩器：缩写旧工具结果，保留最近 N 轮完整。
 *
 * 保护 Read 和 Grep 工具结果不被缩写（LLM 需要看到完整文件/搜索内容）。
 * 从后往前扫描，只缩写非保护的工具结果。
 * 最多缩写 l2MaxCompactions 条。
 */
public class L2MicroCompactor implements Compactor {

    private static final Logger log = LoggerFactory.getLogger(L2MicroCompactor.class);

    private final int l2MaxCompactions;
    private final int l2KeepRecentRounds;

    public L2MicroCompactor(int l2MaxCompactions, int l2KeepRecentRounds) {
        this.l2MaxCompactions = l2MaxCompactions;
        this.l2KeepRecentRounds = l2KeepRecentRounds;
    }

    @Override
    public int order() {
        return 30;
    }

    @Override
    public List<Message> compact(List<Message> messages, Set<Integer> protectedReadIndices) {
        List<int[]> roundBounds = findRoundBounds(messages);
        if (roundBounds.size() <= l2KeepRecentRounds + 1) {
            return messages;
        }

        // 计算最近 l2KeepRecentRounds 轮的消息索引，这些轮次保持原样
        Set<Integer> keepRoundIndices = new HashSet<>();
        for (int r = Math.max(0, roundBounds.size() - l2KeepRecentRounds); r < roundBounds.size(); r++) {
            int[] b = roundBounds.get(r);
            for (int i = b[0]; i <= b[1]; i++) {
                keepRoundIndices.add(i);
            }
        }

        List<Message> result = new ArrayList<>(messages);
        int compacted = 0;

        // 从后往前扫描，缩写非保护 + 非最近轮次的工具结果
        for (int i = result.size() - 1; i >= 0; i--) {
            if (keepRoundIndices.contains(i)) continue;
            if (compacted >= l2MaxCompactions) break;

            Message msg = result.get(i);
            if (msg.getRole() == Message.Role.TOOL && msg.getContent() != null
                    && msg.getContent().length() > 120) {
                // 跳过受保护的索引（Read + Grep）
                if (protectedReadIndices.contains(i)) continue;

                result.set(i, Message.tool(
                        "[Earlier tool result compacted. Re-run if needed.]",
                        msg.getToolCallId()
                ));
                compacted++;
            }
        }

        if (compacted > 0) {
            log.debug("L2: 缩写 {} 条工具结果 (保留最近 {} 轮)", compacted, l2KeepRecentRounds);
        }
        return result;
    }

    /**
     * 计算消息列表中 ReAct 步骤的边界。
     * 每个步骤 = ASSISTANT + 其后的连续 TOOL 结果。
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
}
