package com.codemind.impl.tool;

import com.codemind.api.safety.PermissionLevel;
import com.codemind.api.tool.Tool;
import com.codemind.api.tool.ToolResult;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;

/**
 * 文件读取工具
 * 
 * 学习要点：
 * - 工具的标准实现模式
 * - JSON Schema 参数定义
 * - 文件操作的安全处理
 */
public class ReadTool implements Tool {
    
    private static final ObjectMapper JSON = new ObjectMapper();
    
    @Override
    public String getName() {
        return "Read";  // 新名称
    }
    
    @Override
    public String getDescription() {
        return "读取指定路径的文件内容，支持行号范围限制";
    }
    
    @Override
    public PermissionLevel getDefaultPermission() {
        return PermissionLevel.ALLOW;  // 只读操作，自动允许
    }
    
    /**
     * 向后兼容：旧工具名
     */
    @Override
    public java.util.Optional<String> getDeprecatedName() {
        return java.util.Optional.of("read_file");
    }
    
    @Override
    public JsonNode getInputSchema() {
        ObjectNode schema = JSON.createObjectNode();
        schema.put("type", "object");
        
        ObjectNode properties = schema.putObject("properties");
        
        // path 参数
        ObjectNode pathProp = properties.putObject("path");
        pathProp.put("type", "string");
        pathProp.put("description", "要读取的文件路径");
        
        // offset 参数（可选）
        ObjectNode offsetProp = properties.putObject("offset");
        offsetProp.put("type", "integer");
        offsetProp.put("description", "起始行号（默认为0）");
        
        // limit 参数（可选）
        ObjectNode limitProp = properties.putObject("limit");
        limitProp.put("type", "integer");
        limitProp.put("description", "读取行数（默认读取全部）");
        
        // 必需参数
        schema.putArray("required").add("path");
        
        return schema;
    }
    
    @Override
    public ToolResult execute(Map<String, Object> params) {
        try {
            String pathStr = (String) params.get("path");
            if (pathStr == null || pathStr.isEmpty()) {
                return ToolResult.failure("参数 'path' 是必需的");
            }
            
            Path path = Path.of(pathStr);
            
            if (!Files.exists(path)) {
                return ToolResult.failure("文件不存在: " + pathStr);
            }
            
            if (!Files.isRegularFile(path)) {
                return ToolResult.failure("路径不是文件: " + pathStr);
            }
            
            // 读取文件内容
            String content = Files.readString(path);
            
            // 处理行号范围
            Integer offset = (Integer) params.get("offset");
            Integer limit = (Integer) params.get("limit");
            
            if (offset != null || limit != null) {
                content = extractLines(content, offset, limit);
            }
            
            return ToolResult.success(content);
            
        } catch (Exception e) {
            return ToolResult.failure("读取文件失败: " + e.getMessage());
        }
    }
    
    private String extractLines(String content, Integer offset, Integer limit) {
        String[] lines = content.split("\n");
        int start = offset != null ? offset : 0;
        int end = limit != null ? Math.min(start + limit, lines.length) : lines.length;
        
        StringBuilder sb = new StringBuilder();
        for (int i = start; i < end && i < lines.length; i++) {
            sb.append(i + 1).append(": ").append(lines[i]).append("\n");
        }
        return sb.toString();
    }
}