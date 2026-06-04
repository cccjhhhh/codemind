package com.codemind.impl.skill;

import com.codemind.api.skill.SkillContext;
import com.codemind.api.skill.SkillExecutor;
import com.codemind.api.skill.SkillResult;
import com.codemind.api.tool.ToolResult;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;

import java.nio.file.Path;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 日志分析技能
 * 
 * 工作流程：
 * 1. 解析用户输入，获取日志文件路径
 * 2. 读取日志内容
 * 3. 解析日志格式
 * 4. 识别异常模式（ERROR、WARN、Exception）
 * 5. 统计分析并生成报告
 * 
 * 改进点：
 * - parseLogPath 逻辑更严谨
 * - 支持多种日志路径格式
 * - 更好的错误提示
 */
public class LogAnalysisSkill implements SkillExecutor {
    
    private static final ObjectMapper JSON = new ObjectMapper();
    
    // 常见日志级别模式
    private static final Pattern LOG_LEVEL_PATTERN = Pattern.compile(
        "\\b(ERROR|WARN|WARNING|INFO|DEBUG|FATAL|SEVERE)\\b",
        Pattern.CASE_INSENSITIVE
    );
    
    // 异常堆栈模式
    private static final Pattern EXCEPTION_PATTERN = Pattern.compile(
        "([\\w.]+Exception|[\\w.]+Error)(?::\\s*(.+))?",
        Pattern.CASE_INSENSITIVE
    );
    
    // 时间戳模式
    private static final Pattern TIMESTAMP_PATTERN = Pattern.compile(
        "\\d{4}[-/]\\d{2}[-/]\\d{2}[T ]\\d{2}:\\d{2}:\\d{2}(?:\\.\\d+)?(?:Z|[+-]\\d{2}:?\\d{2})?"
    );
    
    // 日志文件扩展名
    private static final Set<String> LOG_EXTENSIONS = Set.of(
        ".log", ".txt", ".out", ".err"
    );
    
    // 常见日志目录
    private static final Set<String> LOG_DIRS = Set.of(
        "logs", "log", "var/log", "/var/log", "tmp", "temp"
    );
    
    @Override
    public SkillResult execute(SkillContext context) {
        try {
            String userInput = context.getUserInput();
            
            // 解析用户输入，获取日志文件路径
            LogPathInfo pathInfo = parseLogPath(context, userInput);
            
            if (pathInfo.path == null || pathInfo.path.isEmpty()) {
                // 返回引导信息，帮助用户正确输入
                return SkillResult.success(JSON.createObjectNode()
                    .put("status", "need_input")
                    .put("message", "请提供要分析的日志文件路径")
                    .put("examples", "例如：logs/app.log 或 /var/log/syslog")
                    .toString());
            }
            
            // 读取日志文件
            ToolResult readResult = context.callTool("read_file", Map.of("path", pathInfo.path));
            
            if (!readResult.isSuccess()) {
                return SkillResult.success(JSON.createObjectNode()
                    .put("status", "error")
                    .put("message", "无法读取日志文件: " + pathInfo.path)
                    .put("error", readResult.getError())
                    .toString());
            }
            
            String content = readResult.getOutput();
            
            // 分析日志
            LogAnalysis analysis = analyzeLogs(content);
            
            // 构建结果
            ObjectNode result = JSON.createObjectNode();
            result.put("status", "success");
            result.put("file", pathInfo.path);
            result.put("totalLines", content.split("\n").length);
            result.put("errorCount", analysis.errorCount);
            result.put("warnCount", analysis.warnCount);
            result.put("exceptionCount", analysis.exceptions.size());
            
            // 错误摘要
            ArrayNode errorsArray = result.putArray("errors");
            analysis.errors.stream().limit(10).forEach(errorsArray::add);
            
            // 警告摘要
            ArrayNode warnsArray = result.putArray("warnings");
            analysis.warnings.stream().limit(10).forEach(warnsArray::add);
            
            // 异常列表
            ArrayNode exceptionsArray = result.putArray("exceptions");
            analysis.exceptions.entrySet().stream()
                .sorted((a, b) -> b.getValue() - a.getValue())
                .limit(20)
                .forEach(e -> {
                    ObjectNode node = exceptionsArray.addObject();
                    node.put("type", e.getKey());
                    node.put("count", e.getValue());
                });
            
            // 建议
            ArrayNode suggestionsArray = result.putArray("suggestions");
            if (analysis.errorCount > 10) {
                suggestionsArray.add("发现大量错误(" + analysis.errorCount + "个)，建议优先检查系统稳定性");
            }
            if (!analysis.exceptions.isEmpty()) {
                suggestionsArray.add("发现" + analysis.exceptions.size() + "种异常类型，建议逐个排查根因");
            }
            
            return SkillResult.success(result.toString());
            
        } catch (Exception e) {
            return SkillResult.failure("日志分析失败: " + e.getMessage());
        }
    }
    
    /**
     * 日志路径信息
     */
    private static class LogPathInfo {
        String path;
        boolean isAbsolute;
        boolean looksLikeLogFile;
        
        LogPathInfo(String path, boolean isAbsolute, boolean looksLikeLogFile) {
            this.path = path;
            this.isAbsolute = isAbsolute;
            this.looksLikeLogFile = looksLikeLogFile;
        }
    }
    
    /**
     * 从用户输入中解析日志文件路径
     * 
     * 改进：更严谨的路径识别逻辑
     */
    private LogPathInfo parseLogPath(SkillContext context, String userInput) {
        if (userInput == null || userInput.isEmpty()) {
            return new LogPathInfo(null, false, false);
        }
        
        String trimmed = userInput.trim();
        
        // 1. 如果是绝对路径（Windows 或 Unix）
        if (isAbsolutePath(trimmed)) {
            return new LogPathInfo(trimmed, true, looksLikeLogFile(trimmed));
        }
        
        // 2. 如果包含常见日志目录
        for (String logDir : LOG_DIRS) {
            if (trimmed.contains(logDir)) {
                // 可能是日志路径
                return new LogPathInfo(trimmed, false, looksLikeLogFile(trimmed));
            }
        }
        
        // 3. 如果以日志扩展名结尾
        for (String ext : LOG_EXTENSIONS) {
            if (trimmed.toLowerCase().endsWith(ext)) {
                return new LogPathInfo(trimmed, false, true);
            }
        }
        
        // 4. 如果输入很短且看起来像文件名
        if (trimmed.length() <= 100 && looksLikeLogFile(trimmed)) {
            // 结合工作目录尝试解析
            Path workDir = context.getSessionContext().getWorkingDirectory();
            Path candidate = workDir.resolve(trimmed);
            return new LogPathInfo(candidate.toString(), false, true);
        }
        
        // 5. 默认：如果输入看起来是路径（包含路径分隔符）
        if (trimmed.contains("/") || trimmed.contains("\\")) {
            return new LogPathInfo(trimmed, false, looksLikeLogFile(trimmed));
        }
        
        // 不像路径，返回 null 让用户确认
        return new LogPathInfo(null, false, false);
    }
    
    /**
     * 判断是否是绝对路径
     */
    private boolean isAbsolutePath(String path) {
        if (path == null || path.isEmpty()) return false;
        
        // Unix 绝对路径
        if (path.startsWith("/")) return true;
        
        // Windows 绝对路径 (C:\, D:\, etc.)
        if (path.length() >= 2 && Character.isLetter(path.charAt(0)) && path.charAt(1) == ':') {
            return true;
        }
        
        // Windows UNC 路径
        if (path.startsWith("\\\\")) return true;
        
        return false;
    }
    
    /**
     * 判断是否像日志文件
     */
    private boolean looksLikeLogFile(String path) {
        if (path == null) return false;
        String lower = path.toLowerCase();
        
        // 检查扩展名
        for (String ext : LOG_EXTENSIONS) {
            if (lower.endsWith(ext)) return true;
        }
        
        // 检查是否在日志目录中
        for (String logDir : LOG_DIRS) {
            if (lower.contains(logDir)) return true;
        }
        
        return false;
    }
    
    /**
     * 分析日志内容
     */
    private LogAnalysis analyzeLogs(String content) {
        LogAnalysis analysis = new LogAnalysis();
        
        String[] lines = content.split("\n");
        
        for (String line : lines) {
            // 统计日志级别
            Matcher levelMatcher = LOG_LEVEL_PATTERN.matcher(line);
            if (levelMatcher.find()) {
                String level = levelMatcher.group(1).toUpperCase();
                switch (level) {
                    case "ERROR":
                    case "FATAL":
                    case "SEVERE":
                        analysis.errorCount++;
                        analysis.errors.add(truncateLine(line));
                        break;
                    case "WARN":
                    case "WARNING":
                        analysis.warnCount++;
                        analysis.warnings.add(truncateLine(line));
                        break;
                }
            }
            
            // 提取异常
            Matcher exceptionMatcher = EXCEPTION_PATTERN.matcher(line);
            if (exceptionMatcher.find()) {
                String exceptionType = exceptionMatcher.group(1);
                analysis.exceptions.merge(exceptionType, 1, Integer::sum);
            }
        }
        
        return analysis;
    }
    
    /**
     * 截断行
     */
    private String truncateLine(String line) {
        if (line == null) return "";
        if (line.length() <= 200) return line;
        return line.substring(0, 200) + "...";
    }
    
    /**
     * 日志分析结果
     */
    private static class LogAnalysis {
        int errorCount = 0;
        int warnCount = 0;
        List<String> errors = new ArrayList<>();
        List<String> warnings = new ArrayList<>();
        Map<String, Integer> exceptions = new HashMap<>();
    }
}