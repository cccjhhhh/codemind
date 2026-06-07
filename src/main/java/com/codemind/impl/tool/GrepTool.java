package com.codemind.impl.tool;

import com.codemind.api.safety.PermissionLevel;
import com.codemind.api.tool.Tool;
import com.codemind.api.tool.ToolResult;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;

/**
 * 代码搜索工具
 *
 * 改进：
 * - 默认排除 .git、target、node_modules 等目录
 * - 支持读取 .gitignore 获取项目级排除规则
 * - 避免搜索编译产物和版本控制文件
 */
public class GrepTool implements Tool {

    private static final ObjectMapper JSON = new ObjectMapper();
    private static final int DEFAULT_MAX_RESULTS = 50;

    /**
     * 排除规则管理器
     * 在每次 execute 时根据工作目录动态加载
     */
    private ExcludeRules excludeRules;
    
    @Override
    public String getName() {
        return "Grep";  // 新名称
    }
    
    @Override
    public String getDescription() {
        return "在代码库中搜索匹配的代码片段，支持正则表达式";
    }
    
    @Override
    public PermissionLevel getDefaultPermission() {
        return PermissionLevel.ALLOW;  // 只读搜索，自动允许
    }
    
    /**
     * 向后兼容：旧工具名
     */
    @Override
    public java.util.Optional<String> getDeprecatedName() {
        return java.util.Optional.of("search_code");
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

        // 初始化排除规则（每个搜索会话创建新实例）
        if (excludeRules == null) {
            excludeRules = new ExcludeRules();
        }

        try {
            Files.walk(dir)
                .filter(Files::isRegularFile)
                .filter(f -> !excludeRules.shouldExclude(f))  // 排除非源代码文件
                .filter(f -> !isInExcludedDir(f))             // 排除在排除目录内的文件
                .filter(f -> matchesFileType(f, fileType))
                .forEach(file -> searchFile(file, pattern, results, limit));
        } catch (Exception e) {
            // 忽略无法访问的目录
        }
    }

    /**
     * 检查文件是否在排除的目录内
     */
    private boolean isInExcludedDir(Path file) {
        Path absFile = file.toAbsolutePath().normalize();
        Path absDir = file.getRoot();

        // 遍历父目录，检查是否有任何一级目录在排除列表中
        for (Path parent = absFile.getParent(); parent != null && !parent.equals(absDir); parent = parent.getParent()) {
            String dirName = parent.getFileName() != null ? parent.getFileName().toString() : "";
            if (ExcludeRules.getDefaultExcludeDirs().contains(dirName)) {
                return true;
            }
            // 检查完整的父目录路径是否匹配排除模式
            String parentStr = absDir.relativize(parent).toString().replace("\\", "/");
            if (excludeRules.shouldExclude(parentStr)) {
                return true;
            }
        }
        return false;
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