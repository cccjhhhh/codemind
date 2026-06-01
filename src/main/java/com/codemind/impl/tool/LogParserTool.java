package com.codemind.impl.tool;

import com.codemind.api.tool.Tool;
import com.codemind.api.tool.ToolResult;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.regex.Pattern;
import java.util.regex.Matcher;
import java.util.ArrayList;
import java.util.List;

/**
 * 日志解析工具
 * 
 * 学习要点：
 * - 日志格式解析
 * - 正则表达式模式匹配
 * - 异常识别与统计
 */
public class LogParserTool implements Tool {
    
    private static final ObjectMapper JSON = new ObjectMapper();
    
    // 日志级别正则
    private static final Pattern LEVEL_PATTERN = Pattern.compile(
        "\\b(DEBUG|INFO|WARN|WARNING|ERROR|FATAL|TRACE)\\b", 
        Pattern.CASE_INSENSITIVE
    );
    
    @Override
    public String getName() {
        return "parse_logs";
    }
    
    @Override
    public String getDescription() {
        return "解析日志文件，识别异常模式并生成分析报告";
    }
    
    @Override
    public JsonNode getInputSchema() {
        ObjectNode schema = JSON.createObjectNode();
        schema.put("type", "object");
        
        ObjectNode properties = schema.putObject("properties");
        
        ObjectNode pathProp = properties.putObject("path");
        pathProp.put("type", "string");
        pathProp.put("description", "日志文件路径");
        
        ObjectNode levelProp = properties.putObject("level");
        levelProp.put("type", "string");
        levelProp.put("description", "筛选日志级别：DEBUG, INFO, WARN, ERROR");
        
        ObjectNode keywordProp = properties.putObject("keyword");
        keywordProp.put("type", "string");
        keywordProp.put("description", "关键字搜索");
        
        ObjectNode limitProp = properties.putObject("limit");
        limitProp.put("type", "integer");
        limitProp.put("description", "返回的日志条目数量");
        
        schema.putArray("required").add("path");
        
        return schema;
    }
    
    @Override
    public ToolResult execute(Map<String, Object> params) {
        try {
            String pathStr = (String) params.get("path");
            String level = (String) params.get("level");
            String keyword = (String) params.get("keyword");
            Integer limit = (Integer) params.get("limit");
            
            if (pathStr == null || pathStr.isEmpty()) {
                return ToolResult.failure("参数 'path' 是必需的");
            }
            
            Path path = Path.of(pathStr);
            
            if (!Files.exists(path)) {
                return ToolResult.failure("日志文件不存在: " + pathStr);
            }
            
            List<String> lines = Files.readAllLines(path);
            List<LogEntry> entries = parseLogLines(lines, level, keyword);
            
            // 限制结果数量
            if (limit != null && entries.size() > limit) {
                entries = entries.subList(0, limit);
            }
            
            // 生成报告
            String report = generateReport(entries);
            
            return ToolResult.success(report);
            
        } catch (Exception e) {
            return ToolResult.failure("日志解析失败: " + e.getMessage());
        }
    }
    
    private List<LogEntry> parseLogLines(List<String> lines, String level, String keyword) {
        List<LogEntry> entries = new ArrayList<>();
        
        for (String line : lines) {
            if (line == null || line.isEmpty()) continue;
            
            // 级别筛选
            if (level != null && !level.isEmpty()) {
                Matcher m = LEVEL_PATTERN.matcher(line);
                if (m.find()) {
                    String foundLevel = m.group(1).toUpperCase();
                    if (!foundLevel.equals(level.toUpperCase())) {
                        continue;
                    }
                }
            }
            
            // 关键字筛选
            if (keyword != null && !keyword.isEmpty()) {
                if (!line.contains(keyword)) {
                    continue;
                }
            }
            
            entries.add(new LogEntry(line));
        }
        
        return entries;
    }
    
    private String generateReport(List<LogEntry> entries) {
        StringBuilder sb = new StringBuilder();
        sb.append("=== 日志分析报告 ===\n\n");
        sb.append("总条目数: ").append(entries.size()).append("\n\n");
        
        // 按级别统计
        long errorCount = entries.stream().filter(e -> e.isError()).count();
        long warnCount = entries.stream().filter(e -> e.isWarn()).count();
        long infoCount = entries.stream().filter(e -> e.isInfo()).count();
        
        sb.append("级别分布:\n");
        sb.append("  ERROR: ").append(errorCount).append("\n");
        sb.append("  WARN: ").append(warnCount).append("\n");
        sb.append("  INFO: ").append(infoCount).append("\n\n");
        
        sb.append("最近 ").append(Math.min(10, entries.size())).append(" 条日志:\n");
        sb.append("---\n");
        
        int count = 0;
        for (LogEntry entry : entries) {
            if (count++ >= 10) break;
            sb.append(entry.content).append("\n");
        }
        
        return sb.toString();
    }
    
    // 内部类：日志条目
    private static class LogEntry {
        final String content;
        final String level;
        
        LogEntry(String content) {
            this.content = content;
            this.level = extractLevel(content);
        }
        
        private String extractLevel(String line) {
            Matcher m = LEVEL_PATTERN.matcher(line);
            if (m.find()) {
                return m.group(1).toUpperCase();
            }
            return "UNKNOWN";
        }
        
        boolean isError() { return "ERROR".equals(level) || "FATAL".equals(level); }
        boolean isWarn() { return "WARN".equals(level) || "WARNING".equals(level); }
        boolean isInfo() { return "INFO".equals(level); }
    }
}