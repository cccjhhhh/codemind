package com.codemind.impl.hook;

import com.codemind.api.tool.ToolHook;
import com.codemind.api.tool.ToolResult;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Map;

/**
 * 截断钩子 — 大结果落盘（spill），避免 LLM 上下文被撑爆。
 * postExecute 检查工具输出是否超过 spillThreshold（默认 2000 字符），
 * 超过则将完整内容写入 spill 文件，输出替换为头 800 + 尾 400 字符的摘要。
 * 属于第三层防线，接在 MetricsHook 之后。
 */
public class TruncationHook implements ToolHook {
    private static final Logger log = LoggerFactory.getLogger(TruncationHook.class);
    // 摘要常量：保留头 800 字符 + 尾 400 字符，让 LLM 能看到文件两端
    private static final int SUMMARY_HEAD = 800;
    private static final int SUMMARY_TAIL = 400;

    private static final int DEFAULT_SPILL_THRESHOLD = 2000;
    private static final String DEFAULT_SPILL_DIR = ".codemind/spill";

    private final int spillThreshold;
    private final Path spillDir;

    public TruncationHook() {
        this(DEFAULT_SPILL_THRESHOLD, Path.of(DEFAULT_SPILL_DIR));
    }

    public TruncationHook(int spillThreshold, Path spillDir) {
        this.spillThreshold = spillThreshold;
        this.spillDir = spillDir;
    }

    /**
     * 创建带 sessionId 隔离的 spill hook。
     * 文件写入 {spillDir}/{sessionId}/{timestamp}-{toolName}.md，
     * 与 SessionManagerImpl.closeSession 的清理路径一致。
     */
    public TruncationHook(int spillThreshold, String spillDirBase, String sessionId) {
        this.spillThreshold = spillThreshold;
        this.spillDir = Path.of(spillDirBase, sessionId);
    }

    @Override
    public void preExecute(String toolName, Map<String, Object> args) {}

    @Override
    public void postExecute(String toolName, ToolResult result, long elapsedMs) {
        if (!result.isSuccess()) return;

        String output = result.getOutput();
        if (output == null || output.length() <= spillThreshold) return;

        try {
            Files.createDirectories(spillDir);
            String timestamp = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss"));
            String filename = timestamp + "-" + toolName + ".md";
            Path spillFile = spillDir.resolve(filename);

            Files.writeString(spillFile, output);

            // 构造双端摘要：头 SUMMARY_HEAD + 尾 SUMMARY_TAIL
            int len = output.length();
            StringBuilder summary = new StringBuilder();
            String header = "[结果已保存](" + spillFile + ")（共 " + len + " 字符）:\n";
            summary.append(header);
            summary.append(output, 0, Math.min(SUMMARY_HEAD, len));
            if (len > SUMMARY_HEAD + SUMMARY_TAIL) {
                summary.append("\n...\n");
                summary.append(output, len - SUMMARY_TAIL, len);
            }
            result.setOutput(summary.toString());
        } catch (IOException e) {
            log.warn("结果落盘失败: {}", e.getMessage());
        }
    }
}
