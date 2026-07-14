package com.codemind.context;

import com.codemind.llm.Message;
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
 * L1 大结果落盘压缩器：将超过阈值的大工具结果写入 spill 文件，替换为摘要预览。
 *
 * <p>大结果截断落盘入口。
 * 所有工具的大结果都会被落盘到 spill 文件，
 * 并替换为双端预览。
 *
 * <p>阈值由 {@code spillThresholdChars} 控制（默认 50000 字符）。
 */
public class L1SpillCompactor implements Compactor {

    private static final Logger log = LoggerFactory.getLogger(L1SpillCompactor.class);

    private static final int PREVIEW_HEAD = 3000;
    private static final int PREVIEW_TAIL = 2000;

    private final int spillThresholdChars;
    private final Path spillDir;

    public L1SpillCompactor(int spillThresholdChars, Path spillDir) {
        this.spillThresholdChars = spillThresholdChars;
        this.spillDir = spillDir;
    }

    @Override
    public int order() {
        return 10;  // L1: 最先执行
    }

    @Override
    public List<Message> compact(List<Message> messages, Set<Integer> protectedReadIndices) {
        List<Message> result = new ArrayList<>(messages);
        boolean changed = false;

        for (int i = 0; i < result.size(); i++) {
            Message msg = result.get(i);
            if (msg.getRole() == Message.Role.TOOL && msg.getContent() != null
                    && msg.getContent().length() > spillThresholdChars) {
                // 落盘到 spill 文件
                String persistedPath = persist(msg.getContent(), "tool_result");
                // 双端截断预览：保留头 800 + 尾 400 字符
                int len = msg.getContent().length();
                StringBuilder preview = new StringBuilder();
                preview.append("[结果已保存](").append(persistedPath).append(")\n");
                preview.append("[结果过长，仅显示预览]（共 ").append(len).append(" 字符）:\n");
                preview.append(msg.getContent(), 0, Math.min(PREVIEW_HEAD, len));
                if (len > PREVIEW_HEAD + PREVIEW_TAIL) {
                    preview.append("\n...\n");
                    preview.append(msg.getContent(), len - PREVIEW_TAIL, len);
                }
                result.set(i, Message.tool(preview.toString(), msg.getToolCallId()));
                changed = true;
            }
        }

        if (changed) log.debug("L1: 已落盘截断 {} 个大工具结果到 {}", changed ? "若干" : "0", spillDir);
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
            log.warn("L1 落盘失败: {}", e.getMessage());
            return "(persist failed)";
        }
    }
}
