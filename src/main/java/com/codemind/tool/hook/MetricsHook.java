package com.codemind.tool.hook;

import com.codemind.evaluation.EvaluationCollector;
import com.codemind.tool.spi.ToolHook;
import com.codemind.tool.ToolResult;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class MetricsHook implements ToolHook {

    private static final Logger log = LoggerFactory.getLogger(MetricsHook.class);

    private final Map<String, Integer> toolCallCounts = new ConcurrentHashMap<>();
    private final Map<String, Integer> toolFailureCounts = new ConcurrentHashMap<>();

    // 评估收集器 (可选)
    private EvaluationCollector evaluationCollector;

    // 用于记录工具调用开始时间
    private final ThreadLocal<Long> toolStartTime = new ThreadLocal<>();

    public MetricsHook() {
        this(null);
    }

    public MetricsHook(EvaluationCollector evaluationCollector) {
        this.evaluationCollector = evaluationCollector;
    }

    /**
     * 设置评估收集器
     */
    public void setEvaluationCollector(EvaluationCollector collector) {
        this.evaluationCollector = collector;
    }

    @Override
    public void preExecute(String toolName, Map<String, Object> args) {
        toolCallCounts.merge(toolName, 1, Integer::sum);
        toolStartTime.set(System.currentTimeMillis());
    }

    @Override
    public void postExecute(String toolName, ToolResult result, long elapsedMs) {
        int resultLen = result.isSuccess() ? (result.getOutput() != null ? result.getOutput().length() : 0) : 0;
        log.info("Tool {} 执行了 {}ms，结果 {} 字符，状态: {}",
            toolName, elapsedMs, resultLen, result.isSuccess() ? "成功" : "失败");

        if (!result.isSuccess()) {
            toolFailureCounts.merge(toolName, 1, Integer::sum);
        }

        // 记录到评估收集器
        if (evaluationCollector != null) {
            Long startTime = toolStartTime.get();
            long duration = startTime != null ? System.currentTimeMillis() - startTime : elapsedMs;
            evaluationCollector.recordToolCall(toolName, new ConcurrentHashMap<>(), result, duration);
        }

        toolStartTime.remove();
    }

    /**
     * 获取工具调用统计
     */
    public Map<String, Integer> getToolCallCounts() {
        return new ConcurrentHashMap<>(toolCallCounts);
    }

    /**
     * 获取工具失败统计
     */
    public Map<String, Integer> getToolFailureCounts() {
        return new ConcurrentHashMap<>(toolFailureCounts);
    }

    /**
     * 重置统计
     */
    public void reset() {
        toolCallCounts.clear();
        toolFailureCounts.clear();
    }
}
