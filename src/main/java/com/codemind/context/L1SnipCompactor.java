package com.codemind.context;

import com.codemind.llm.Message;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/**
 * L1 裁剪压缩器：裁剪中间旧消息。
 *
 * 保留头 N 条 + 尾 M 条，中间被裁剪的部分用一条占位消息替换。
 * 确保不拆散 ASSISTANT(tool_call) + TOOL(result) 的配对。
 */
public class L1SnipCompactor implements Compactor {

    private static final Logger log = LoggerFactory.getLogger(L1SnipCompactor.class);

    private final int maxMessagesBeforeSnip;
    private final int keepHead;

    public L1SnipCompactor(int maxMessagesBeforeSnip) {
        this(maxMessagesBeforeSnip, 3);
    }

    public L1SnipCompactor(int maxMessagesBeforeSnip, int keepHead) {
        this.maxMessagesBeforeSnip = maxMessagesBeforeSnip;
        this.keepHead = keepHead;
    }

    @Override
    public int order() {
        return 10;
    }

    @Override
    public List<Message> compact(List<Message> messages, Set<Integer> protectedReadIndices) {
        if (messages.size() <= maxMessagesBeforeSnip) {
            return messages;
        }

        int keepTail = maxMessagesBeforeSnip - keepHead;
        int headEnd = keepHead;
        int tailStart = messages.size() - keepTail;

        // 边界保护：不拆散 ASSISTANT + TOOL 配对
        while (headEnd > 0 && headEnd < messages.size()
                && messages.get(headEnd).getRole() == Message.Role.TOOL) {
            headEnd++;
        }
        while (tailStart > 0 && tailStart < messages.size()
                && messages.get(tailStart).getRole() == Message.Role.TOOL) {
            tailStart--;
        }
        if (headEnd > 0 && hasToolCall(messages.get(headEnd - 1))) {
            while (headEnd < messages.size()
                    && messages.get(headEnd).getRole() == Message.Role.TOOL) {
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

        log.debug("L1: 裁剪 {} 条消息 (总计 {}→{})", snipped, messages.size(), result.size());
        return result;
    }

    private boolean hasToolCall(Message msg) {
        return msg.getRole() == Message.Role.ASSISTANT && msg.hasToolCalls();
    }
}
