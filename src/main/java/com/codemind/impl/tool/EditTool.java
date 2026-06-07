package com.codemind.impl.tool;

import com.codemind.api.safety.PermissionLevel;
import com.codemind.api.tool.Tool;
import com.codemind.api.tool.ToolResult;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.Optional;

/**
 * 文件编辑工具
 * 
 * 对文件进行增量修改，支持：
 * - 字符串替换（必须完全匹配）
 * - 行号范围读取
 * 
 * 安全特性：
 * - 路径遍历检测
 * - 原子写入（先写临时文件，再替换）
 * 
 * 参考设计：Claude Code Edit Tool
 */
public class EditTool implements Tool {
    
    private static final ObjectMapper JSON = new ObjectMapper();
    private static final int MAX_FILE_SIZE = 1024 * 1024; // 1MB
    
    @Override
    public String getName() {
        return "Edit";
    }
    
    @Override
    public String getDescription() {
        return "对文件进行增量修改。必须提供精确的 oldString 进行替换。";
    }
    
    @Override
    public PermissionLevel getDefaultPermission() {
        return PermissionLevel.ASK;  // 修改文件，需要确认
    }
    
    @Override
    public Optional<String> getDeprecatedName() {
        return Optional.empty();  // 无废弃名称
    }
    
    @Override
    public JsonNode getInputSchema() {
        ObjectNode schema = JSON.createObjectNode();
        schema.put("type", "object");
        
        ObjectNode properties = schema.putObject("properties");
        
        ObjectNode filePathProp = properties.putObject("filePath");
        filePathProp.put("type", "string");
        filePathProp.put("description", "要修改的文件绝对路径");
        
        ObjectNode oldStringProp = properties.putObject("oldString");
        oldStringProp.put("type", "string");
        oldStringProp.put("description", "要替换的文本（必须精确匹配）");
        
        ObjectNode newStringProp = properties.putObject("newString");
        newStringProp.put("type", "string");
        newStringProp.put("description", "替换后的文本");
        
        ObjectNode replaceAllProp = properties.putObject("replaceAll");
        replaceAllProp.put("type", "boolean");
        replaceAllProp.put("description", "是否替换所有匹配（默认 false）");
        
        schema.putArray("required").add("filePath").add("oldString").add("newString");
        
        return schema;
    }
    
    @Override
    public ToolResult execute(Map<String, Object> params) {
        try {
            String filePath = (String) params.get("filePath");
            String oldString = (String) params.get("oldString");
            String newString = (String) params.get("newString");
            Boolean replaceAll = (Boolean) params.get("replaceAll");
            
            // 参数验证
            if (filePath == null || filePath.isEmpty()) {
                return ToolResult.failure("参数 'filePath' 是必需的");
            }
            if (oldString == null) {
                return ToolResult.failure("参数 'oldString' 是必需的");
            }
            if (newString == null) {
                return ToolResult.failure("参数 'newString' 是必需的");
            }
            
            // 路径遍历检测
            if (isPathTraversal(filePath)) {
                return ToolResult.failure("检测到路径遍历攻击，拒绝访问: " + filePath);
            }
            
            Path path = Path.of(filePath);
            
            // 检查文件是否存在
            if (!Files.exists(path)) {
                return ToolResult.failure("文件不存在: " + filePath);
            }
            
            if (!Files.isRegularFile(path)) {
                return ToolResult.failure("路径不是文件: " + filePath);
            }
            
            // 检查文件大小
            long fileSize = Files.size(path);
            if (fileSize > MAX_FILE_SIZE) {
                return ToolResult.failure("文件过大（>1MB），建议分段编辑");
            }
            
            // 读取文件内容
            String content = Files.readString(path);
            
            // 检查 oldString 是否存在
            if (!content.contains(oldString)) {
                return ToolResult.failure("未找到要替换的文本。请确保 oldString 完全匹配。");
            }
            
            // 检查是否有多个匹配
            int count = countOccurrences(content, oldString);
            if (count > 1 && !Boolean.TRUE.equals(replaceAll)) {
                return ToolResult.failure(
                    "找到 " + count + " 处匹配。如需替换所有，请设置 replaceAll=true。\n" +
                    "或者提供更多上下文以唯一标识要替换的位置。"
                );
            }
            
            // 执行替换
            String newContent;
            if (Boolean.TRUE.equals(replaceAll)) {
                newContent = content.replace(oldString, newString);
            } else {
                // 只替换第一个匹配
                int index = content.indexOf(oldString);
                newContent = content.substring(0, index) + newString + 
                            content.substring(index + oldString.length());
            }
            
            // 原子写入（先写临时文件，再替换）
            Path tempFile = Files.createTempFile(path.getParent(), "edit_", ".tmp");
            try {
                Files.writeString(tempFile, newContent);
                Files.move(tempFile, path, java.nio.file.StandardCopyOption.REPLACE_EXISTING, 
                          java.nio.file.StandardCopyOption.ATOMIC_MOVE);
            } catch (IOException e) {
                // 清理临时文件
                Files.deleteIfExists(tempFile);
                throw e;
            }
            
            return ToolResult.success(
                "文件修改成功: " + filePath + "\n" +
                "替换了 " + (Boolean.TRUE.equals(replaceAll) ? count : 1) + " 处"
            );
            
        } catch (Exception e) {
            return ToolResult.failure("编辑文件失败: " + e.getMessage());
        }
    }
    
    /**
     * 检测路径遍历攻击
     */
    private boolean isPathTraversal(String path) {
        return path.contains("..") || path.contains("~");
    }
    
    /**
     * 统计字符串出现次数
     */
    private int countOccurrences(String text, String target) {
        int count = 0;
        int index = 0;
        while ((index = text.indexOf(target, index)) != -1) {
            count++;
            index += target.length();
        }
        return count;
    }
}
