package com.codemind.context;

import com.codemind.llm.Message;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/**
 * L1 轮次裁剪压缩器：砍掉最旧 N 个完整轮次。
 *
 * 从最旧轮次开始，完整删除整个轮次(USER+ASSISTANT+TOOLs)。
 * 保留至少 3 轮最新对话，确保当前工作上下文不丢失。
 * 每次删除后重新计算轮次边界，避免旧模拟程序的索引偏移 Bug。
 */
public class L1SnipCompactor implements Compactor {

    private static final Logger log = LoggerFactory.getLogger(L1SnipCompactor.class);

    private final int l1MaxRounds;
    private static final int MIN_KEEP_ROUNDS = 3;

    public L1SnipCompactor(int l1MaxRounds) {
        this.l1MaxRounds = l1MaxRounds;
    }

    @Override
    public int order() {
        return 20;
    }

    @Override
    public List<Message> compact(List<Message> messages, Set<Integer> protectedReadIndices) {
        List<int[]> roundBounds = findRoundBounds(messages);
        int totalRounds = roundBounds.size();

        // 不足 MIN_KEEP_ROUNDS+1 轮，不做压缩
        if (totalRounds <= MIN_KEEP_ROUNDS + 1) {
            return messages;
        }

        int roundsToRemove = Math.min(l1MaxRounds, totalRounds - MIN_KEEP_ROUNDS);
        List<Message> result = new ArrayList<>(messages);

        // 逐轮删除最旧轮次，每次删除后重算边界
        for (int removed = 0; removed < roundsToRemove; removed++) {
            List<int[]> currentBounds = findRoundBounds(result);
            if (currentBounds.size() <= MIN_KEEP_ROUNDS) break;

            int[] oldest = currentBounds.get(0);
            // 从后往前删除，保持索引正确
            for (int j = oldest[1]; j >= oldest[0]; j--) {
                result.remove(j);
            }
        }

        log.debug("L1: 砍掉 {} 轮 (剩余 {} 轮)", roundsToRemove,
            findRoundBounds(result).size());
        return result;
    }

    /**
     * 计算消息列表中 ReAct 步骤的边界。
     * 每个步骤 = ASSISTANT(含工具调用) + 其后的连续 TOOL 结果。
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
