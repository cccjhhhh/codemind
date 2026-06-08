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

public class TruncationHook implements ToolHook {

    private static final Logger log = LoggerFactory.getLogger(TruncationHook.class);
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

            String summary = output.substring(0, Math.min(200, output.length()));
            result.setOutput("[结果已保存](" + spillFile + ")（前 " + summary.length() + " 字符摘要）:\n" + summary);
        } catch (IOException e) {
            log.warn("结果落盘失败: {}", e.getMessage());
        }
    }
}
