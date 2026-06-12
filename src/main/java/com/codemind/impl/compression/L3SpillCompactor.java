package com.codemind.impl.compression;

import com.codemind.core.service.compression.Compactor;
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
import java.util.Set;

/**
 * L3 落盘压缩器：将大工具结果写入 spill 文件，替换为摘要。
 *
 * 【harness 规则 03-compression-single-entry】
 * 这是唯一的大结果落盘入口。
 * TruncationHook 不再做落盘，只做预览。
 * Read 工具结果受 protectedReadIndices 保护，不落盘。
 */
public class L3SpillCompactor implements Compactor {

    private static final Logger log = LoggerFactory.getLogger(L3SpillCompactor.class);

    private final int spillThresholdChars;
    private final Path spillDir;
    private final String sessionId;

    public L3SpillCompactor(int spillThresholdChars, Path spillDir, String sessionId) {
        this.spillThresholdChars = spillThresholdChars;
        this.spillDir = spillDir.resolve(sessionId);
        this.sessionId = sessionId;
    }

    @Override
    public int order() {
        return 30;
    }

    @Override
    public List<Message> compact(List<Message> messages, Set<Integer> protectedReadIndices) {
        List<Message> result = new ArrayList<>(messages);
        boolean changed = false;

        for (int i = 0; i < result.size(); i++) {
            Message msg = result.get(i);
            if (msg.getRole() == Message.Role.TOOL && msg.getContent() != null
                    && msg.getContent().length() > spillThresholdChars) {
                // 保护 Read 结果：不落盘
                if (protectedReadIndices.contains(i)) continue;

                String persistedPath = persist(msg.getContent(), "tool_result");
                String preview = msg.getContent().substring(0, Math.min(200, msg.getContent().length()));
                result.set(i, Message.tool(
                        "[结果已保存](" + persistedPath + ")\n预览: " + preview,
                        msg.getToolCallId()
                ));
                changed = true;
            }
        }

        if (changed) log.debug("L3: 已落盘大工具结果到 {}", spillDir);
        return result;
    }

    private String persist(String content, String prefix) {
        try {
            String ts = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss"));
            Files.createDirectories(spillDir);
            Path file = spillDir.resolve(ts + "-" + prefix + ".md");
            Files.writeString(file, content);
            return file.toString();
        } catch (IOException e) {
            log.warn("L3 落盘失败: {}", e.getMessage());
            return "(persist failed)";
        }
    }
}
