package com.codemind.evaluation;

import com.codemind.agent.AgentLoop;
import com.codemind.agent.spi.AgentResult;
import com.codemind.config.Settings;
import com.codemind.config.SettingsLoader;
import com.codemind.session.SessionContext;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.*;
import java.util.concurrent.*;

/**
 * CodeMind 评估运行器 - 自动执行评估任务并收集指标。
 */
public class EvaluationRunner {

    private static final Logger log = LoggerFactory.getLogger(EvaluationRunner.class);
    private static final ObjectMapper mapper = new ObjectMapper();

    private final Settings settings;
    private final EvaluationCollector collector;
    private final String projectPath;

    public EvaluationRunner(String projectPath, String configPath) throws Exception {
        this.projectPath = projectPath;
        if (configPath != null) {
            this.settings = SettingsLoader.loadChain(Path.of(configPath));
        } else {
            this.settings = SettingsLoader.loadChain(Path.of(projectPath));
        }
        this.collector = new EvaluationCollector();
    }

    /**
     * 运行评估任务集
     */
    public EvaluationReport runEvaluation(String taskFile, String outputDir) throws Exception {
        log.info("开始评估: {}", taskFile);

        // 加载任务
        List<Map<String, Object>> tasks = loadTasks(taskFile);
        log.info("加载 {} 个任务", tasks.size());

        // 执行每个任务
        for (Map<String, Object> task : tasks) {
            executeTask(task);
        }

        // 生成报告
        EvaluationReport report = collector.generateReport(settings.getCurrentModel());
        report.calculateSummary();

        // 保存报告
        Path output = Paths.get(outputDir);
        Files.createDirectories(output);
        collector.saveReport(report, outputDir);

        log.info("评估完成: Pass@1={:.1f}%", report.getPassAt1() * 100);
        return report;
    }

    /**
     * 执行单个任务
     */
    private void executeTask(Map<String, Object> task) {
        String taskId = (String) task.get("id");
        String description = (String) task.get("description");
        String difficulty = (String) task.get("difficulty");
        int maxIterations = task.containsKey("max_iterations") ? (Integer) task.get("max_iterations") : 20;
        int timeoutSeconds = task.containsKey("timeout_seconds") ? (Integer) task.get("timeout_seconds") : 300;

        log.info("执行任务: {} - {}", taskId, description);

        collector.startTask(taskId, description, difficulty);

        try {
            // 创建 Agent
            AgentLoop agent = createAgent();

            // 创建会话上下文
            SessionContext context = new SessionContext(projectPath);

            // 执行 Agent
            long startTime = System.currentTimeMillis();
            AgentResult result = agent.run(description, context);
            long duration = System.currentTimeMillis() - startTime;

            // 记录 token 使用 (从上下文估算)
            int estimatedTokens = estimateTokens(context);
            collector.recordTokenUsage(estimatedTokens / 2, estimatedTokens / 2);

            // 判断成功
            boolean success = result.isSuccess();
            String reason = success ? "completed" : result.getError();

            collector.endTask(success, reason);

            log.info("任务 {} 完成: 成功={}, 耗时={}ms", taskId, success, duration);

        } catch (Exception e) {
            log.error("任务 {} 执行异常", taskId, e);
            collector.markFailed(e.getMessage());
        }
    }

    /**
     * 创建 Agent 实例
     */
    private AgentLoop createAgent() throws Exception {
        // 使用反射创建 Agent 实例
        // 这里简化处理，实际应该根据 Settings 创建
        Class<?> bootstrapperClass = Class.forName("com.codemind.bootstrap.CodeMindBootstrapper");
        Object bootstrapper = bootstrapperClass.getMethod("create", Settings.class, String.class)
            .invoke(null, settings, projectPath);

        return (AgentLoop) bootstrapperClass.getMethod("getAgentLoop")
            .invoke(bootstrapper);
    }

    /**
     * 估算 token 使用量
     */
    private int estimateTokens(SessionContext context) {
        // 简单估算：每4个字符约1个token
        int totalChars = 0;
        for (var msg : context.getHistory()) {
            totalChars += msg.getContent() != null ? msg.getContent().length() : 0;
        }
        return totalChars / 4;
    }

    /**
     * 加载任务文件
     */
    private List<Map<String, Object>> loadTasks(String taskFile) throws IOException {
        JsonNode root = mapper.readTree(new File(taskFile));
        JsonNode tasksNode = root.get("tasks");

        List<Map<String, Object>> tasks = new ArrayList<>();
        for (JsonNode taskNode : tasksNode) {
            Map<String, Object> task = mapper.convertValue(taskNode, Map.class);
            tasks.add(task);
        }
        return tasks;
    }

    /**
     * 主入口
     */
    public static void main(String[] args) throws Exception {
        if (args.length < 2) {
            System.out.println("用法: EvaluationRunner <projectPath> <taskFile> [outputDir]");
            System.exit(1);
        }

        String projectPath = args[0];
        String taskFile = args[1];
        String outputDir = args.length > 2 ? args[2] : "evaluation/results";

        EvaluationRunner runner = new EvaluationRunner(projectPath, null);
        EvaluationReport report = runner.runEvaluation(taskFile, outputDir);

        // 输出摘要
        System.out.println("\n========== 评估结果 ==========");
        System.out.printf("Pass@1: %.1f%%\n", report.getPassAt1() * 100);
        System.out.printf("总任务: %d\n", report.getTotalTasks());
        System.out.printf("成功: %d\n", report.getSuccessfulTasks());
        System.out.printf("失败: %d\n", report.getFailedTasks());
        System.out.printf("平均耗时: %.1f ms\n", report.getAvgDurationMs());
        System.out.printf("平均 Token: %.0f\n", report.getAvgTokens());
        System.out.printf("总成本: $%.4f\n", report.getTotalCostUsd());
        System.out.println("==============================");
    }
}
