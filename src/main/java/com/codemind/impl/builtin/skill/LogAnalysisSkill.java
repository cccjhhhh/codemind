package com.codemind.impl.builtin.skill;

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

public class LogAnalysisSkill implements SkillExecutor {
    
    private static final ObjectMapper JSON = new ObjectMapper();
    
    private static final Pattern LOG_LEVEL_PATTERN = Pattern.compile(
        "\\b(ERROR|WARN|WARNING|INFO|DEBUG|FATAL|SEVERE)\\b",
        Pattern.CASE_INSENSITIVE
    );
    
    private static final Pattern EXCEPTION_PATTERN = Pattern.compile(
        "([\\w.]+Exception|[\\w.]+Error)(?::\\s*(.+))?",
        Pattern.CASE_INSENSITIVE
    );
    
    private static final Set<String> LOG_EXTENSIONS = Set.of(
        ".log", ".txt", ".out", ".err"
    );
    
    private static final Set<String> LOG_DIRS = Set.of(
        "logs", "log", "var/log", "/var/log", "tmp", "temp"
    );
    
    @Override
    public SkillResult execute(SkillContext context) {
        try {
            String userInput = context.getUserInput();
            LogPathInfo pathInfo = parseLogPath(context, userInput);
            
            if (pathInfo.path == null || pathInfo.path.isEmpty()) {
                return SkillResult.success(JSON.createObjectNode()
                    .put("status", "need_input")
                    .put("message", "请提供要分析的日志文件路径")
                    .put("examples", "例如：logs/app.log 或 /var/log/syslog")
                    .toString());
            }
            
            ToolResult readResult = context.callTool("read_file", Map.of("path", pathInfo.path));
            
            if (!readResult.isSuccess()) {
                return SkillResult.success(JSON.createObjectNode()
                    .put("status", "error")
                    .put("message", "无法读取日志文件: " + pathInfo.path)
                    .put("error", readResult.getError())
                    .toString());
            }
            
            String content = readResult.getOutput();
            LogAnalysis analysis = analyzeLogs(content);
            
            ObjectNode result = JSON.createObjectNode();
            result.put("status", "success");
            result.put("file", pathInfo.path);
            result.put("totalLines", content.split("\n").length);
            result.put("errorCount", analysis.errorCount);
            result.put("warnCount", analysis.warnCount);
            result.put("exceptionCount", analysis.exceptions.size());
            
            ArrayNode errorsArray = result.putArray("errors");
            analysis.errors.stream().limit(10).forEach(errorsArray::add);
            
            ArrayNode warnsArray = result.putArray("warnings");
            analysis.warnings.stream().limit(10).forEach(warnsArray::add);
            
            ArrayNode exceptionsArray = result.putArray("exceptions");
            analysis.exceptions.entrySet().stream()
                .sorted((a, b) -> b.getValue() - a.getValue())
                .limit(20)
                .forEach(e -> {
                    ObjectNode node = exceptionsArray.addObject();
                    node.put("type", e.getKey());
                    node.put("count", e.getValue());
                });
            
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
    
    private LogPathInfo parseLogPath(SkillContext context, String userInput) {
        if (userInput == null || userInput.isEmpty()) {
            return new LogPathInfo(null, false, false);
        }
        
        String trimmed = userInput.trim();
        
        if (isAbsolutePath(trimmed)) {
            return new LogPathInfo(trimmed, true, looksLikeLogFile(trimmed));
        }
        
        for (String logDir : LOG_DIRS) {
            if (trimmed.contains(logDir)) {
                return new LogPathInfo(trimmed, false, looksLikeLogFile(trimmed));
            }
        }
        
        for (String ext : LOG_EXTENSIONS) {
            if (trimmed.toLowerCase().endsWith(ext)) {
                return new LogPathInfo(trimmed, false, true);
            }
        }
        
        if (trimmed.length() <= 100 && looksLikeLogFile(trimmed)) {
            Path workDir = context.getSessionContext().getWorkingDirectory();
            Path candidate = workDir.resolve(trimmed);
            return new LogPathInfo(candidate.toString(), false, true);
        }
        
        if (trimmed.contains("/") || trimmed.contains("\\")) {
            return new LogPathInfo(trimmed, false, looksLikeLogFile(trimmed));
        }
        
        return new LogPathInfo(null, false, false);
    }
    
    private boolean isAbsolutePath(String path) {
        if (path == null || path.isEmpty()) return false;
        
        if (path.startsWith("/")) return true;
        
        if (path.length() >= 2 && Character.isLetter(path.charAt(0)) && path.charAt(1) == ':') {
            return true;
        }
        
        if (path.startsWith("\\\\")) return true;
        
        return false;
    }
    
    private boolean looksLikeLogFile(String path) {
        if (path == null) return false;
        String lower = path.toLowerCase();
        
        for (String ext : LOG_EXTENSIONS) {
            if (lower.endsWith(ext)) return true;
        }
        
        for (String logDir : LOG_DIRS) {
            if (lower.contains(logDir)) return true;
        }
        
        return false;
    }
    
    private LogAnalysis analyzeLogs(String content) {
        LogAnalysis analysis = new LogAnalysis();
        
        String[] lines = content.split("\n");
        
        for (String line : lines) {
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
            
            Matcher exceptionMatcher = EXCEPTION_PATTERN.matcher(line);
            if (exceptionMatcher.find()) {
                String exceptionType = exceptionMatcher.group(1);
                analysis.exceptions.merge(exceptionType, 1, Integer::sum);
            }
        }
        
        return analysis;
    }
    
    private String truncateLine(String line) {
        if (line == null) return "";
        if (line.length() <= 200) return line;
        return line.substring(0, 200) + "...";
    }
    
    private static class LogAnalysis {
        int errorCount = 0;
        int warnCount = 0;
        List<String> errors = new ArrayList<>();
        List<String> warnings = new ArrayList<>();
        Map<String, Integer> exceptions = new HashMap<>();
    }
}
