package com.codemind.impl.cli;

import com.codemind.api.tool.Tool;
import com.codemind.api.tool.ToolRegistry;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * 系统提示词构建器
 * 
 * 动态构建系统提示词，包含：
 * - 可用工具列表
 * - 权限规则
 * - 输出格式
 * - 安全边界
 * 
 * 学习要点：模板化设计、动态内容注入
 * 参考设计：Claude Code System Prompt
 */
public class SystemPromptBuilder {
    
    private final ToolRegistry toolRegistry;

    public SystemPromptBuilder(ToolRegistry toolRegistry) {
        this.toolRegistry = toolRegistry;
    }
    
    /**
     * 从模板构建系统提示词
     */
    public String build() {
        String template = loadTemplate();
        
        return template
            .replace("{{TOOL_LIST}}", buildToolList())
            .replace("{{PERMISSION_RULES}}", buildPermissionRules());
    }
    
    /**
     * 加载模板
     */
    private String loadTemplate() {
        // 尝试从文件加载
        Path templatePath = Path.of("src/main/resources/prompts/system.md");
        if (Files.exists(templatePath)) {
            try {
                return Files.readString(templatePath);
            } catch (IOException e) {
                // 使用默认模板
            }
        }
        
        return getDefaultTemplate();
    }
    
    /**
     * 构建工具列表
     */
    private String buildToolList() {
        StringBuilder sb = new StringBuilder();
        sb.append("### 可用工具\n\n");
        
        for (var def : toolRegistry.getAllDefinitions()) {
            var func = def.getFunction();
            sb.append("- **").append(func.getName());
            sb.append("**: ").append(func.getDescription().split("\n")[0]);
            sb.append("\n");
        }
        
        return sb.toString();
    }
    
    /**
     * 构建权限规则
     */
    private String buildPermissionRules() {
        StringBuilder sb = new StringBuilder();
        sb.append("### 权限规则\n\n");
        sb.append("| 工具 | 默认级别 | 说明 |\n");
        sb.append("|------|----------|------|\n");
        
        for (var def : toolRegistry.getAllDefinitions()) {
            String name = def.getFunction().getName();
            Tool tool = toolRegistry.get(name);
            String level = tool != null ? tool.getDefaultPermission().name() : "ASK";
            sb.append("| ").append(name);
            sb.append(" | ").append(level);
            sb.append(" | ").append(getLevelDescription(level));
            sb.append(" |\n");
        }
        
        return sb.toString();
    }
    
    /**
     * 获取权限级别描述
     */
    private String getLevelDescription(String level) {
        return switch (level) {
            case "ALLOW" -> "自动允许";
            case "ASK" -> "需确认";
            case "DENY" -> "禁止";
            default -> "未知";
        };
    }
    
    /**
     * 默认模板
     */
    private String getDefaultTemplate() {
        return """
            # CodeMind 系统提示词
            
            ## 角色
            你是一个安全的 AI 编程助手，名叫 CodeMind。
            
            {{TOOL_LIST}}
            
            {{PERMISSION_RULES}}
            
            ## 输出格式
            所有输出使用中文。
            """;
    }
}
