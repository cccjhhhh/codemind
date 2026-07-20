package com.codemind.evaluation;

import com.codemind.tool.ToolResult;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 评估指标收集器 - 收集 Agent 执行过程中的所有指标。
 *
 * <p>使用方式:</p>
 * <pre>
 *   EvaluationCollector collector = new EvaluationCollector();
 *   collector.startTask("T1-001", "读取文件", "easy");
 *
 *   // 在工具调用时记录
 *   collector.recordToolCall("ReadTool", args, result, durationMs);
 *
 *   // 记录 token 使用
 *   collector.recordTokenUsage(1500, 800);
 *
 *   // 结束任务
 *   collector.endTask(true, "completed");
 *
 *   // 生成报告
 *   EvaluationReport report = collector.generateReport("gpt-4");
 *   collector.saveReport(report, "evaluation/results/");
 * </pre>
 */
public class EvaluationCollector {

    private static final Logger log = LoggerFactory.getLogger(EvaluationCollector.class);

    // 每个任务的线程安全存储
    private final Map<String, TaskResult> taskResults = new ConcurrentHashMap<>();

    // 当前活跃任务 ID (线程本地)
    private final ThreadLocal<String> currentTaskId = new ThreadLocal<>();

    // Token 定价 (USD per 1K tokens)
    private static final double INPUT_TOKEN_COST = 0.00003;  // $0.03 / 1K tokens
    private static final double OUTPUT_TOKEN_COST = 0.00006; // $0.06 / 1K tokens

    /**
     * 开始一个新的评估任务
     */
    public void startTask(String taskId, String description, String difficulty) {
        TaskResult result = new TaskResult(taskId, description, difficulty);
        taskResults.put(taskId, result);
        currentTaskId.set(taskId);
        log.info("开始评估任务: {} - {}", taskId, description);
    }

    /**
     * 记录工具调用
     */
    public void recordToolCall(String toolName, Map<String, Object> args,
                               ToolResult result, long durationMs) {
        String taskId = currentTaskId.get();
        if (taskId == null) {
            log.warn("没有活跃任务，忽略工具调用记录");
            return;
        }

        TaskResult taskResult = taskResults.get(taskId);
        if (taskResult == null) return;

        String errorMessage = result.isSuccess() ? null :
            (result.getOutput() != null ? result.getOutput().substring(0, Math.min(200, result.getOutput().length())) : "Unknown error");

        int outputLength = result.getOutput() != null ? result.getOutput().length() : 0;

        ToolCallRecord record = new ToolCallRecord(
            toolName,
            args,
            result.isSuccess(),
            outputLength,
            durationMs,
            errorMessage
        );

        taskResult.recordToolCall(record);
    }

    /**
     * 记录 token 使用
     */
    public void recordTokenUsage(int inputTokens, int outputTokens) {
        String taskId = currentTaskId.get();
        if (taskId == null) return;

        TaskResult taskResult = taskResults.get(taskId);
        if (taskResult == null) return;

        taskResult.setInputTokens(taskResult.getInputTokens() + inputTokens);
        taskResult.setOutputTokens(taskResult.getOutputTokens() + outputTokens);

        // 计算成本
        double cost = (inputTokens * INPUT_TOKEN_COST) + (outputTokens * OUTPUT_TOKEN_COST);
        taskResult.setEstimatedCostUsd(taskResult.getEstimatedCostUsd() + cost);
    }

    /**
     * 记录迭代次数
     */
    public void incrementIterations() {
        String taskId = currentTaskId.get();
        if (taskId == null) return;

        TaskResult taskResult = taskResults.get(taskId);
        if (taskResult != null) {
            taskResult.setTotalIterations(taskResult.getTotalIterations() + 1);
        }
    }

    /**
     * 结束任务
     */
    public void endTask(boolean success, String reason) {
        String taskId = currentTaskId.get();
        if (taskId == null) return;

        TaskResult taskResult = taskResults.get(taskId);
        if (taskResult != null) {
            taskResult.markCompleted(success, reason);
            log.info("任务完成: {} - 成功={}, 耗时={}ms, Token={}",
                taskId, success, taskResult.getDurationMs(), taskResult.getTotalTokens());
        }

        currentTaskId.remove();
    }

    /**
     * 标记任务超时
     */
    public void markTimeout() {
        endTask(false, "timeout");
    }

    /**
     * 标记任务失败
     */
    public void markFailed(String errorMessage) {
        String taskId = currentTaskId.get();
        if (taskId == null) return;

        TaskResult taskResult = taskResults.get(taskId);
        if (taskResult != null) {
            taskResult.setErrorMessage(errorMessage);
        }
        endTask(false, "error");
    }

    /**
     * 生成评估报告
     */
    public EvaluationReport generateReport(String modelName) {
        EvaluationReport report = new EvaluationReport();
        report.setModelName(modelName);

        // 添加所有任务结果
        taskResults.values().forEach(report::addTaskResult);

        // 计算汇总指标
        report.calculateSummary();

        log.info("评估报告生成完成: Pass@1={:.1f}%, 总任务={}, 成功={}",
            report.getPassAt1() * 100, report.getTotalTasks(), report.getSuccessfulTasks());

        return report;
    }

    /**
     * 保存报告到文件
     */
    public void saveReport(EvaluationReport report, String outputDir) throws IOException {
        Path dir = Paths.get(outputDir);
        Files.createDirectories(dir);

        // 保存 JSON 报告
        Path jsonPath = dir.resolve("evaluation_report.json");
        Files.writeString(jsonPath, report.toJson());
        log.info("报告已保存: {}", jsonPath);

        // 保存 Markdown 摘要
        Path mdPath = dir.resolve("evaluation_summary.md");
        Files.writeString(mdPath, generateMarkdownSummary(report));
        log.info("摘要已保存: {}", mdPath);
    }

    /**
     * 生成 Markdown 格式的摘要
     */
    private String generateMarkdownSummary(EvaluationReport report) {
        StringBuilder sb = new StringBuilder();

        sb.append("# CodeMind Evaluation Report\n\n");
        sb.append(String.format("**生成时间:** %s\n\n", report.getGeneratedAt()));
        sb.append(String.format("**模型:** %s\n\n", report.getModelName()));

        sb.append("## 核心指标\n\n");
        sb.append("| 指标 | 值 |\n");
        sb.append("|------|----|\n");
        sb.append(String.format("| Pass@1 | **%.1f%%** |\n", report.getPassAt1() * 100));
        sb.append(String.format("| 总任务数 | %d |\n", report.getTotalTasks()));
        sb.append(String.format("| 成功任务 | %d |\n", report.getSuccessfulTasks()));
        sb.append(String.format("| 失败任务 | %d |\n", report.getFailedTasks()));
        sb.append(String.format("| 超时任务 | %d |\n", report.getTimeoutTasks()));
        sb.append(String.format("| 平均耗时 | %.1f ms |\n", report.getAvgDurationMs()));
        sb.append(String.format("| 平均 Token | %.0f |\n", report.getAvgTokens()));
        sb.append(String.format("| 总 Token | %d |\n", report.getTotalTokens()));
        sb.append(String.format("| 平均成本 | $%.4f |\n", report.getAvgCostUsd()));
        sb.append(String.format("| 总成本 | $%.4f |\n", report.getTotalCostUsd()));
        sb.append(String.format("| 工具成功率 | %.1f%% |\n", report.getAvgToolSuccessRate() * 100));

        sb.append("\n## 难度分布\n\n");
        sb.append("| 难度 | 总数 | 成功 | 通过率 | 平均耗时 | 平均 Token |\n");
        sb.append("|------|------|------|--------|----------|------------|\n");

        report.getDifficultyBreakdown().forEach((diff, stats) -> {
            sb.append(String.format("| %s | %d | %d | %.1f%% | %.1f ms | %.0f |\n",
                diff, stats.total, stats.successful, stats.passRate * 100,
                stats.avgDurationMs, stats.avgTokens));
        });

        sb.append("\n## 工具使用统计\n\n");
        sb.append("| 工具 | 调用次数 |\n");
        sb.append("|------|----------|\n");

        report.getToolUsageSummary().entrySet().stream()
            .sorted((a, b) -> b.getValue() - a.getValue())
            .forEach(entry -> {
                sb.append(String.format("| %s | %d |\n", entry.getKey(), entry.getValue()));
            });

        sb.append("\n## 任务详情\n\n");
        sb.append("| 任务 ID | 描述 | 难度 | 状态 | 耗时 | Token | 工具调用 |\n");
        sb.append("|---------|------|------|------|------|-------|----------|\n");

        report.getTaskResults().forEach(task -> {
            String status = task.isSuccess() ? "✅" : "❌";
            sb.append(String.format("| %s | %s | %s | %s | %d ms | %d | %d |\n",
                task.getTaskId(),
                task.getDescription().substring(0, Math.min(30, task.getDescription().length())),
                task.getDifficulty(),
                status,
                task.getDurationMs(),
                task.getTotalTokens(),
                task.getToolCallCount()));
        });

        return sb.toString();
    }

    /**
     * 获取当前收集的所有结果
     */
    public List<TaskResult> getAllResults() {
        return new ArrayList<>(taskResults.values());
    }

    /**
     * 获取指定任务的结果
     */
    public TaskResult getResult(String taskId) {
        return taskResults.get(taskId);
    }

    /**
     * 清除所有结果
     */
    public void clear() {
        taskResults.clear();
        currentTaskId.remove();
    }
}
