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
 * 代码搜索工具
 * 
 * 学习要点：
 * - 正则表达式搜索
 * - 多目录递归搜索
 * - 结果高亮与格式化
 */
public class CodeSearchTool implements Tool {
    
    private static final ObjectMapper JSON = new ObjectMapper();
    private static final int DEFAULT_MAX_RESULTS = 50;
    
    @Override
    public String getName() {
        return "search_code";
    }
    
    @Override
    public String getDescription() {
        return "在代码库中搜索匹配的代码片段，支持正则表达式";
    }
    
    @Override
    public JsonNode getInputSchema() {
        ObjectNode schema = JSON.createObjectNode();
        schema.put("type", "object");
        
        ObjectNode properties = schema.putObject("properties");
        
        ObjectNode patternProp = properties.putObject("pattern");
        patternProp.put("type", "string");
        patternProp.put("description", "搜索模式（支持正则表达式）");
        
        ObjectNode pathProp = properties.putObject("path");
        pathProp.put("type", "string");
        pathProp.put("description", "搜索路径（默认为当前目录）");
        
        ObjectNode fileTypeProp = properties.putObject("file_type");
        fileTypeProp.put("type", "string");
        fileTypeProp.put("description", "文件类型过滤，如 java, py, js");
        
        ObjectNode maxResultsProp = properties.putObject("max_results");
        maxResultsProp.put("type", "integer");
        maxResultsProp.put("description", "最大结果数");
        
        schema.putArray("required").add("pattern");
        
        return schema;
    }
    
    @Override
    public ToolResult execute(Map<String, Object> params) {
        try {
            String patternStr = (String) params.get("pattern");
            String pathStr = (String) params.get("path");
            String fileType = (String) params.get("file_type");
            Integer maxResults = (Integer) params.get("max_results");
            
            if (patternStr == null || patternStr.isEmpty()) {
                return ToolResult.failure("参数 'pattern' 是必需的");
            }
            
            Path searchPath = pathStr != null ? Path.of(pathStr) : Path.of(".");
            int limit = maxResults != null ? maxResults : DEFAULT_MAX_RESULTS;
            
            Pattern pattern = Pattern.compile(patternStr);
            List<String> results = new ArrayList<>();
            
            searchDirectory(searchPath, pattern, fileType, results, limit);
            
            if (results.isEmpty()) {
                return ToolResult.success("未找到匹配结果");
            }
            
            return ToolResult.success(String.join("\n---\n", results));
            
        } catch (Exception e) {
            return ToolResult.failure("搜索失败: " + e.getMessage());
        }
    }
    
    private void searchDirectory(Path dir, Pattern pattern, String fileType, 
                                 List<String> results, int limit) {
        if (results.size() >= limit) return;
        
        try {
            Files.walk(dir)
                .filter(Files::isRegularFile)
                .filter(f -> matchesFileType(f, fileType))
                .forEach(file -> searchFile(file, pattern, results, limit));
        } catch (Exception e) {
            // 忽略无法访问的目录
        }
    }
    
    private boolean matchesFileType(Path file, String fileType) {
        if (fileType == null || fileType.isEmpty()) {
            return true;
        }
        String fileName = file.getFileName().toString();
        return fileName.endsWith("." + fileType);
    }
    
    private void searchFile(Path file, Pattern pattern, List<String> results, int limit) {
        try {
            List<String> lines = Files.readAllLines(file);
            
            for (int i = 0; i < lines.size() && results.size() < limit; i++) {
                String line = lines.get(i);
                if (pattern.matcher(line).find()) {
                    String context = String.format("%s:%d: %s", file, i + 1, line);
                    results.add(context);
                }
            }
        } catch (Exception e) {
            // 忽略无法读取的文件
        }
    }
}