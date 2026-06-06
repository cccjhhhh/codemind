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
 * 文件写入工具
 * 
 * 学习要点：
 * - 文件写入的安全处理
 * - 目录自动创建
 * - 追加模式 vs 覆盖模式
 */
public class WriteTool implements Tool {
    
    private static final ObjectMapper JSON = new ObjectMapper();
    
    @Override
    public String getName() {
        return "Write";  // 新名称
    }
    
    @Override
    public String getDescription() {
        return "将内容写入指定文件，支持覆盖和追加模式";
    }
    
    @Override
    public PermissionLevel getDefaultPermission() {
        return PermissionLevel.ASK;  // 写入操作，需要用户确认
    }
    
    /**
     * 向后兼容：旧工具名
     */
    @Override
    public java.util.Optional<String> getDeprecatedName() {
        return java.util.Optional.of("write_file");
    }
    
    @Override
    public JsonNode getInputSchema() {
        // 创建 JSON Schema 对象
        ObjectNode schema = JSON.createObjectNode();
        schema.put("type", "object");
        
        // 创建 properties 对象
        ObjectNode properties = schema.putObject("properties");
        
        // path 参数（必填）
        ObjectNode pathProp = properties.putObject("path");
        pathProp.put("type", "string");
        pathProp.put("description", "要写入的文件路径");
        
        // content 参数（必填）
        ObjectNode contentProp = properties.putObject("content");
        contentProp.put("type", "string");
        contentProp.put("description", "要写入的文件内容");
        
        // append 参数（可选）
        ObjectNode appendProp = properties.putObject("append");
        appendProp.put("type", "boolean");
        appendProp.put("description", "是否追加模式（默认 false，即覆盖）");
        
        // 必填参数列表
        schema.putArray("required").add("path").add("content");
        
        return schema;
    }
    
    @Override
    public ToolResult execute(Map<String, Object> params) {
        try {
            // 1. 获取必填参数
            String pathStr = (String) params.get("path");
            String content = (String) params.get("content");
            
            // 2. 参数校验
            if (pathStr == null || pathStr.isEmpty()) {
                return ToolResult.failure("参数 'path' 是必需的");
            }
            if (content == null) {
                return ToolResult.failure("参数 'content' 是必需的");
            }
            
            // 3. 获取可选参数（追加模式）
            Boolean append = (Boolean) params.get("append");
            boolean isAppend = append != null && append;
            
            // 4. 转换为 Path 对象
            Path path = Path.of(pathStr);
            
            // 5. 安全检查：不能写入系统关键目录
            if (!isPathSafe(pathStr)) {
                return ToolResult.failure("禁止写入系统目录: " + pathStr);
            }
            
            // 6. 如果是追加模式，检查文件是否存在
            if (isAppend && Files.exists(path) && !Files.isRegularFile(path)) {
                return ToolResult.failure("追加模式要求目标是一个已存在的文件");
            }
            
            // 7. 创建父目录（如果不存在）
            Path parentDir = path.getParent();
            if (parentDir != null && !Files.exists(parentDir)) {
                Files.createDirectories(parentDir);
            }
            
            // 8. 写入文件
            if (isAppend) {
                // 追加模式
                Files.writeString(path, content, java.nio.file.StandardOpenOption.APPEND);
            } else {
                // 覆盖模式（如果文件存在则覆盖）
                Files.writeString(path, content);
            }
            
            // 9. 返回成功结果
            String message = isAppend ? "内容已追加到文件" : "文件写入成功";
            return ToolResult.success(message + ": " + pathStr);
            
        } catch (Exception e) {
            return ToolResult.failure("写入文件失败: " + e.getMessage());
        }
    }
    
    /**
     * 安全检查：防止写入系统关键目录
     */
    private boolean isPathSafe(String pathStr) {
        String lowerPath = pathStr.toLowerCase();
        
        // 禁止写入系统关键目录
        String[] unsafePaths = {
            "c:\\windows",
            "c:\\program files",
            "/etc",
            "/usr/bin",
            "/usr/sbin",
            "/bin",
            "/sbin"
        };
        
        for (String unsafe : unsafePaths) {
            if (lowerPath.startsWith(unsafe)) {
                return false;
            }
        }
        return true;
    }
}