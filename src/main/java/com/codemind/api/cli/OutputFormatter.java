package com.codemind.api.cli;

import com.codemind.api.safety.Permission;
import com.codemind.api.tool.ToolResult;
import java.util.Map;

/**
 * CLI 输出格式化接口
 * 负责将各种输出内容格式化为带 ANSI 样式的字符串
 */
public interface OutputFormatter {
    
    /**
     * 格式化工具调用开始
     */
    String formatToolCallStart(String toolName, Map<String, Object> params);
    
    /**
     * 格式化工具调用结果
     */
    String formatToolCallEnd(String toolName, ToolResult result);
    
    /**
     * 格式化权限请求提示
     */
    String formatPermissionPrompt(Permission permission, String context);
    
    /**
     * 格式化错误信息
     */
    String formatError(String message);
    
    /**
     * 格式化成功信息
     */
    String formatSuccess(String message);
    
    /**
     * 格式化思考过程（可选）
     */
    default String formatThinking(String content) {
        return content;
    }
}