package com.codemind.tool.hook;

import com.codemind.tool.spi.ToolHook;
import com.codemind.tool.ToolResult;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class MetricsHook implements ToolHook {

    private static final Logger log = LoggerFactory.getLogger(MetricsHook.class);
    private static final ObjectMapper JSON = new ObjectMapper();

    private final Map<String, Integer> toolCallCounts = new ConcurrentHashMap<>();
    private final Map<String, Integer> toolFailureCounts = new ConcurrentHashMap<>();

    @Override
    public void preExecute(String toolName, Map<String, Object> args) {
        toolCallCounts.merge(toolName, 1, Integer::sum);
    }

    @Override
    public void postExecute(String toolName, ToolResult result, long elapsedMs) {
        int resultLen = result.isSuccess() ? (result.getOutput() != null ? result.getOutput().length() : 0) : 0;
        log.info("Tool {} 执行了 {}ms，结果 {} 字符，状态: {}",
            toolName, elapsedMs, resultLen, result.isSuccess() ? "成功" : "失败");

        if (!result.isSuccess()) {
            toolFailureCounts.merge(toolName, 1, Integer::sum);
        }
    }

}
