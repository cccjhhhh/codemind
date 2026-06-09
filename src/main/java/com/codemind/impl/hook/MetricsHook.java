package com.codemind.impl.hook;

import com.codemind.api.tool.ToolHook;
import com.codemind.api.tool.ToolResult;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * 指标钩子 — 记录工具调用的执行耗时、结果大小、调用次数。
 * preExecute 递增调用计数器，postExecute 打印 INFO 日志（toolName, 耗时, 结果长度, 状态）。
 * 无副作用，仅用于监控和调试。
 */
public class MetricsHook implements ToolHook {

    private static final Logger log = LoggerFactory.getLogger(MetricsHook.class);
    private final Map<String, AtomicInteger> callCounts = new ConcurrentHashMap<>();

    @Override
    public void preExecute(String toolName, Map<String, Object> args) {
        callCounts.computeIfAbsent(toolName, k -> new AtomicInteger()).incrementAndGet();
    }

    @Override
    public void postExecute(String toolName, ToolResult result, long elapsedMs) {
        int resultLen = result.isSuccess() ? (result.getOutput() != null ? result.getOutput().length() : 0) : 0;
        log.info("Tool {} 执行了 {}ms，结果 {} 字符，状态: {}",
            toolName, elapsedMs, resultLen, result.isSuccess() ? "成功" : "失败");
    }
}
