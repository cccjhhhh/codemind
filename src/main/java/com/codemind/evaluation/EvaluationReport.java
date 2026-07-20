package com.codemind.evaluation;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 评估报告 - 汇总所有任务的执行结果。
 */
public class EvaluationReport {

    @JsonProperty("report_id")
    private String reportId;

    @JsonProperty("model_name")
    private String modelName;

    @JsonProperty("total_tasks")
    private int totalTasks;

    @JsonProperty("successful_tasks")
    private int successfulTasks;

    @JsonProperty("failed_tasks")
    private int failedTasks;

    @JsonProperty("timeout_tasks")
    private int timeoutTasks;

    @JsonProperty("pass_at_1")
    private double passAt1;

    @JsonProperty("avg_duration_ms")
    private double avgDurationMs;

    @JsonProperty("avg_iterations")
    private double avgIterations;

    @JsonProperty("avg_tool_calls")
    private double avgToolCalls;

    @JsonProperty("avg_tokens")
    private double avgTokens;

    @JsonProperty("total_tokens")
    private long totalTokens;

    @JsonProperty("avg_cost_usd")
    private double avgCostUsd;

    @JsonProperty("total_cost_usd")
    private double totalCostUsd;

    @JsonProperty("avg_tool_success_rate")
    private double avgToolSuccessRate;

    @JsonProperty("tool_usage_summary")
    private Map<String, Integer> toolUsageSummary;

    @JsonProperty("difficulty_breakdown")
    private Map<String, DifficultyStats> difficultyBreakdown;

    @JsonProperty("task_results")
    private List<TaskResult> taskResults;

    @JsonProperty("generated_at")
    private String generatedAt;

    public EvaluationReport() {
        this.toolUsageSummary = new HashMap<>();
        this.difficultyBreakdown = new HashMap<>();
        this.taskResults = new ArrayList<>();
        this.reportId = "eval-" + System.currentTimeMillis();
        this.generatedAt = java.time.Instant.now().toString();
    }

    /**
     * 添加任务结果并更新统计
     */
    public void addTaskResult(TaskResult result) {
        taskResults.add(result);
        totalTasks++;

        if (result.isSuccess()) {
            successfulTasks++;
        } else if ("timeout".equals(result.getTerminationReason())) {
            timeoutTasks++;
        } else {
            failedTasks++;
        }

        // 更新工具使用统计
        result.getToolUsageCounts().forEach((tool, count) ->
            toolUsageSummary.merge(tool, count, Integer::sum));
    }

    /**
     * 计算汇总指标
     */
    public void calculateSummary() {
        if (totalTasks == 0) return;

        passAt1 = (double) successfulTasks / totalTasks;

        avgDurationMs = taskResults.stream()
            .mapToLong(TaskResult::getDurationMs)
            .average()
            .orElse(0.0);

        avgIterations = taskResults.stream()
            .mapToInt(TaskResult::getTotalIterations)
            .average()
            .orElse(0.0);

        avgToolCalls = taskResults.stream()
            .mapToInt(TaskResult::getToolCallCount)
            .average()
            .orElse(0.0);

        avgTokens = taskResults.stream()
            .mapToInt(TaskResult::getTotalTokens)
            .average()
            .orElse(0.0);

        totalTokens = taskResults.stream()
            .mapToLong(TaskResult::getTotalTokens)
            .sum();

        avgCostUsd = taskResults.stream()
            .mapToDouble(TaskResult::getEstimatedCostUsd)
            .average()
            .orElse(0.0);

        totalCostUsd = taskResults.stream()
            .mapToDouble(TaskResult::getEstimatedCostUsd)
            .sum();

        avgToolSuccessRate = taskResults.stream()
            .mapToDouble(TaskResult::getToolSuccessRate)
            .average()
            .orElse(1.0);

        // 按难度分组统计
        taskResults.forEach(result -> {
            String diff = result.getDifficulty();
            difficultyBreakdown.computeIfAbsent(diff, k -> new DifficultyStats()).addResult(result);
        });
    }

    /**
     * 生成 JSON 格式的报告
     */
    public String toJson() {
        try {
            com.fasterxml.jackson.databind.ObjectMapper mapper = new com.fasterxml.jackson.databind.ObjectMapper();
            mapper.setSerializationInclusion(com.fasterxml.jackson.annotation.JsonInclude.Include.NON_NULL);
            return mapper.writerWithDefaultPrettyPrinter().writeValueAsString(this);
        } catch (Exception e) {
            return "{\"error\": \"Failed to serialize report\"}";
        }
    }

    // 难度级别统计
    public static class DifficultyStats {
        @JsonProperty("total")
        public int total = 0;

        @JsonProperty("successful")
        public int successful = 0;

        @JsonProperty("pass_rate")
        public double passRate = 0.0;

        @JsonProperty("avg_duration_ms")
        public double avgDurationMs = 0.0;

        @JsonProperty("avg_tokens")
        public double avgTokens = 0.0;

        private long totalDuration = 0;
        private long totalTokens = 0;

        public void addResult(TaskResult result) {
            total++;
            if (result.isSuccess()) successful++;
            totalDuration += result.getDurationMs();
            totalTokens += result.getTotalTokens();
            passRate = (double) successful / total;
            avgDurationMs = (double) totalDuration / total;
            avgTokens = (double) totalTokens / total;
        }
    }

    // Getters and Setters
    public String getReportId() { return reportId; }
    public void setReportId(String reportId) { this.reportId = reportId; }

    public String getModelName() { return modelName; }
    public void setModelName(String modelName) { this.modelName = modelName; }

    public int getTotalTasks() { return totalTasks; }
    public int getSuccessfulTasks() { return successfulTasks; }
    public int getFailedTasks() { return failedTasks; }
    public int getTimeoutTasks() { return timeoutTasks; }

    public double getPassAt1() { return passAt1; }
    public double getAvgDurationMs() { return avgDurationMs; }
    public double getAvgIterations() { return avgIterations; }
    public double getAvgToolCalls() { return avgToolCalls; }
    public double getAvgTokens() { return avgTokens; }
    public long getTotalTokens() { return totalTokens; }
    public double getAvgCostUsd() { return avgCostUsd; }
    public double getTotalCostUsd() { return totalCostUsd; }
    public double getAvgToolSuccessRate() { return avgToolSuccessRate; }

    public Map<String, Integer> getToolUsageSummary() { return toolUsageSummary; }
    public Map<String, DifficultyStats> getDifficultyBreakdown() { return difficultyBreakdown; }
    public List<TaskResult> getTaskResults() { return taskResults; }

    public String getGeneratedAt() { return generatedAt; }
}
