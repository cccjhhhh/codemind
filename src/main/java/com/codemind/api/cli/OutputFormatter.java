package com.codemind.api.cli;

import com.codemind.api.safety.Permission;
import com.codemind.api.tool.ToolResult;

import java.util.Map;

/**
 * CLI 输出格式化接口
 * 
 * 负责将各种输出内容格式化为带 ANSI 样式的字符串。
 * 支持：
 * - 工具调用（开始/结束）
 * - Skill 调用（开始/进度/结束）
 * - 权限请求提示
 * - 思考过程
 * 
 * 设计参考：Claude Code、Cursor、Aider 等主流 Agent CLI
 */
public interface OutputFormatter {
    
    /**
     * 格式化工具调用开始
     * 
     * @param toolName 工具名称
     * @param params 工具参数
     * @return 格式化后的字符串
     */
    String formatToolCallStart(String toolName, Map<String, Object> params);
    
    /**
     * 格式化工具调用结果
     * 
     * @param toolName 工具名称
     * @param result 工具执行结果
     * @return 格式化后的字符串
     */
    String formatToolCallEnd(String toolName, ToolResult result);
    
    /**
     * 格式化权限请求提示
     * 
     * @param permission 需要的权限
     * @param context 权限请求上下文（如工具名称）
     * @return 格式化后的字符串
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
    
    // ========== Skill 格式化方法（新增）==========
    
    /**
     * 格式化 Skill 调用开始
     * 
     * @param skillName Skill 名称
     * @param description Skill 描述
     * @return 格式化后的字符串
     */
    default String formatSkillCallStart(String skillName, String description) {
        return "\n🎯 " + skillName + ": " + description + "...";
    }
    
    /**
     * 格式化 Skill 执行进度阶段
     * 
     * @param stage 当前阶段名称
     * @return 格式化后的字符串
     */
    default String formatSkillProgress(String stage) {
        return "  ↳ " + stage + "...";
    }
    
    /**
     * 格式化 Skill 调用结束
     * 
     * @param skillName Skill 名称
     * @param success 是否成功
     * @param summary 结果摘要
     * @return 格式化后的字符串
     */
    default String formatSkillCallEnd(String skillName, boolean success, String summary) {
        if (success) {
            return " ✓ 完成" + (summary != null ? " → " + summary : "") + "\n";
        } else {
            return " ✗ 失败\n";
        }
    }
}