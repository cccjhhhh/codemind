package com.codemind.evaluation;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 单个评估任务的执行结果。
 */
public class TaskResult {

    @JsonProperty("task_id")
    private String taskId;

    @JsonProperty("description")
    private String description;

    @JsonProperty("difficulty")
    private String difficulty;

    @JsonProperty("success")
    private boolean success;

    @JsonProperty("termination_reason")
    private String terminationReason;

    @JsonProperty("start_time")
    private long startTime;

    @JsonProperty("end_time")
    private long endTime;

    @JsonProperty("duration_ms")
    private long durationMs;

    @JsonProperty("total_iterations")
    private int totalIterations;

    @JsonProperty("tool_call_count")
    private int toolCallCount;

    @JsonProperty("tool_calls")
    private List<ToolCallRecord> toolCalls;

    @JsonProperty("tool_usage_counts")
    private Map<String, Integer> toolUsageCounts;

    @JsonProperty("tool_failure_counts")
    private Map<String, Integer> toolFailureCounts;

    @JsonProperty("input_tokens")
    private int inputTokens;

    @JsonProperty("output_tokens")
    private int outputTokens;

    @JsonProperty("total_tokens")
    private int totalTokens;

    @JsonProperty("estimated_cost_usd")
    private double estimatedCostUsd;

    @JsonProperty("error_message")
    private String errorMessage;

    public TaskResult() {
        this.toolCalls = new ArrayList<>();
        this.toolUsageCounts = new HashMap<>();
        this.toolFailureCounts = new HashMap<>();
    }

    public TaskResult(String taskId, String description, String difficulty) {
        this();
        this.taskId = taskId;
        this.description = description;
        this.difficulty = difficulty;
        this.startTime = System.currentTimeMillis();
    }

    /**
     * 记录工具调用
     */
    public void recordToolCall(ToolCallRecord record) {
        toolCalls.add(record);
        toolCallCount++;

        String toolName = record.getToolName();
        toolUsageCounts.merge(toolName, 1, Integer::sum);

        if (!record.isSuccess()) {
            toolFailureCounts.merge(toolName, 1, Integer::sum);
        }
    }

    /**
     * 标记任务完成
     */
    public void markCompleted(boolean success, String reason) {
        this.endTime = System.currentTimeMillis();
        this.durationMs = endTime - startTime;
        this.success = success;
        this.terminationReason = reason;
        this.totalTokens = inputTokens + outputTokens;
    }

    /**
     * 计算工具调用成功率
     */
    public double getToolSuccessRate() {
        if (toolCallCount == 0) return 1.0;
        int failures = toolFailureCounts.values().stream().mapToInt(Integer::intValue).sum();
        return (double) (toolCallCount - failures) / toolCallCount;
    }

    /**
     * 获取每种工具的调用次数
     */
    public Map<String, Integer> getToolUsageBreakdown() {
        return new HashMap<>(toolUsageCounts);
    }

    // Getters and Setters
    public String getTaskId() { return taskId; }
    public void setTaskId(String taskId) { this.taskId = taskId; }

    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }

    public String getDifficulty() { return difficulty; }
    public void setDifficulty(String difficulty) { this.difficulty = difficulty; }

    public boolean isSuccess() { return success; }
    public void setSuccess(boolean success) { this.success = success; }

    public String getTerminationReason() { return terminationReason; }
    public void setTerminationReason(String terminationReason) { this.terminationReason = terminationReason; }

    public long getStartTime() { return startTime; }
    public void setStartTime(long startTime) { this.startTime = startTime; }

    public long getEndTime() { return endTime; }
    public void setEndTime(long endTime) { this.endTime = endTime; }

    public long getDurationMs() { return durationMs; }
    public void setDurationMs(long durationMs) { this.durationMs = durationMs; }

    public int getTotalIterations() { return totalIterations; }
    public void setTotalIterations(int totalIterations) { this.totalIterations = totalIterations; }

    public int getToolCallCount() { return toolCallCount; }
    public void setToolCallCount(int toolCallCount) { this.toolCallCount = toolCallCount; }

    public List<ToolCallRecord> getToolCalls() { return toolCalls; }
    public void setToolCalls(List<ToolCallRecord> toolCalls) { this.toolCalls = toolCalls; }

    public Map<String, Integer> getToolUsageCounts() { return toolUsageCounts; }
    public void setToolUsageCounts(Map<String, Integer> toolUsageCounts) { this.toolUsageCounts = toolUsageCounts; }

    public Map<String, Integer> getToolFailureCounts() { return toolFailureCounts; }
    public void setToolFailureCounts(Map<String, Integer> toolFailureCounts) { this.toolFailureCounts = toolFailureCounts; }

    public int getInputTokens() { return inputTokens; }
    public void setInputTokens(int inputTokens) { this.inputTokens = inputTokens; }

    public int getOutputTokens() { return outputTokens; }
    public void setOutputTokens(int outputTokens) { this.outputTokens = outputTokens; }

    public int getTotalTokens() { return totalTokens; }
    public void setTotalTokens(int totalTokens) { this.totalTokens = totalTokens; }

    public double getEstimatedCostUsd() { return estimatedCostUsd; }
    public void setEstimatedCostUsd(double estimatedCostUsd) { this.estimatedCostUsd = estimatedCostUsd; }

    public String getErrorMessage() { return errorMessage; }
    public void setErrorMessage(String errorMessage) { this.errorMessage = errorMessage; }
}
