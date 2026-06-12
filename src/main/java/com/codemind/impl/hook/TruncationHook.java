package com.codemind.impl.hook;

import com.codemind.api.tool.ToolHook;
import com.codemind.api.tool.ToolResult;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Map;

/**
 * 截断钩子 — 大结果摘要。
 *
 * 【harness 规则 03-compression-single-entry】
 * - 只做摘要预览（保留头 800 字符 + 尾 400 字符）
 * - 不做落盘（落盘统一由 CompactionPipeline L3 处理）
 *
 * 注意：Read 工具的结果不做截断，LLM 需要看到完整源码。
 */
public class TruncationHook implements ToolHook {
    private static final Logger log = LoggerFactory.getLogger(TruncationHook.class);

    private static final int SUMMARY_HEAD = 800;
    private static final int SUMMARY_TAIL = 400;
    private static final int DEFAULT_SPILL_THRESHOLD = 2000;

    private final int spillThreshold;

    public TruncationHook() {
        this(DEFAULT_SPILL_THRESHOLD);
    }

    public TruncationHook(int spillThreshold) {
        this.spillThreshold = spillThreshold;
    }

    @Override
    public void preExecute(String toolName, Map<String, Object> args) {}

    @Override
    public void postExecute(String toolName, ToolResult result, long elapsedMs) {
        if (!result.isSuccess()) return;

        // Read 工具结果不做截断 — LLM 需要完整源码
        if ("Read".equals(toolName)) return;

        String output = result.getOutput();
        if (output == null || output.length() <= spillThreshold) return;

        // 仅做双端摘要预览，不做落盘
        int len = output.length();
        StringBuilder summary = new StringBuilder();
        summary.append("[结果过长，仅显示预览]（共 ").append(len).append(" 字符）:\n");
        summary.append(output, 0, Math.min(SUMMARY_HEAD, len));
        if (len > SUMMARY_HEAD + SUMMARY_TAIL) {
            summary.append("\n...\n");
            summary.append(output, len - SUMMARY_TAIL, len);
        }
        result.setOutput(summary.toString());

        log.debug("TruncationHook: {} 输出过长({}字符)，已双端截断", toolName, len);
    }
}
