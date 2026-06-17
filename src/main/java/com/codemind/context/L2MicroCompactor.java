package com.codemind.context;

import com.codemind.llm.Message;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/**
 * L2 微压缩器：用占位符替换旧工具结果。
 *
 * 保护 Read 工具结果不被压缩（LLM 需要看到完整文件内容）。
 * 保护 ProtectedReadIndices 中标记的索引。
 */
public class L2MicroCompactor implements Compactor {

    private static final Logger log = LoggerFactory.getLogger(L2MicroCompactor.class);

    private final int keepRecentToolResults;

    public L2MicroCompactor(int keepRecentToolResults) {
        this.keepRecentToolResults = keepRecentToolResults;
    }

    @Override
    public int order() {
        return 20;
    }

    @Override
    public List<Message> compact(List<Message> messages, Set<Integer> protectedReadIndices) {
        List<Message> result = new ArrayList<>(messages);
        int toolCount = 0;

        for (int i = result.size() - 1; i >= 0; i--) {
            Message msg = result.get(i);
            if (msg.getRole() == Message.Role.TOOL) {
                toolCount++;
                if (toolCount > keepRecentToolResults && msg.getContent() != null
                        && msg.getContent().length() > 120) {
                    // 保护 Read 结果：不被压缩
                    if (protectedReadIndices.contains(i)) continue;
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
