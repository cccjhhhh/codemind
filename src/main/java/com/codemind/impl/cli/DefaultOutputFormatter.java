package com.codemind.impl.cli;

import com.codemind.api.cli.OutputFormatter;
import com.codemind.api.safety.Permission;
import com.codemind.api.tool.ToolResult;

import java.util.Map;

/**
 * 默认输出格式化器
 * 
 * 提供美观的 CLI 输出格式，支持：
 * - 工具调用（带参数摘要）
 * - Skill 调用（带阶段进度）
 * - 权限请求（带上下文）
 * 
 * 设计参考：Claude Code、Cursor、Aider 等主流 Agent CLI
 */
public class DefaultOutputFormatter implements OutputFormatter {
    
    // 工具图标映射
    private static final Map<String, String> TOOL_ICONS = Map.of(
        "execute_command", "⚙️",
        "read_file", "📄",
        "write_file", "✏️",
        "search_code", "🔍",
        "code_review", "📊"
    );
    
    // Skill 图标
    private static final String SKILL_ICON = "🎯";
    
    @Override
    public String formatToolCallStart(String toolName, Map<String, Object> params) {
        StringBuilder sb = new StringBuilder();
        
        String icon = TOOL_ICONS.getOrDefault(toolName, "🔧");
        String displayName = getDisplayName(toolName, params);
        
        sb.append("\n")
          .append(AnsiStyles.DIM).append("┌─ ").append(AnsiStyles.RESET)
          .append(icon).append(" ")
          .append(AnsiStyles.BOLD).append(AnsiStyles.CYAN).append(toolName).append(AnsiStyles.RESET)
          .append(AnsiStyles.DIM).append(": ").append(AnsiStyles.RESET)
          .append(AnsiStyles.YELLOW).append(displayName).append(AnsiStyles.RESET)
          .append("\n");
        
        // 显示关键参数（最多 2 个）
        int paramCount = 0;
        for (Map.Entry<String, Object> entry : params.entrySet()) {
            if (paramCount >= 2) break;
            if (isKeyParam(toolName, entry.getKey())) {
                String valueStr = formatParamValue(entry.getValue());
                sb.append(AnsiStyles.DIM)
                  .append("│  ")
                  .append(AnsiStyles.RESET)
                  .append(entry.getKey())
                  .append(": ")
                  .append(AnsiStyles.DIM).append(valueStr).append(AnsiStyles.RESET)
                  .append("\n");
                paramCount++;
            }
        }
        
        sb.append(AnsiStyles.DIM).append("└─ ").append(AnsiStyles.RESET)
          .append("执行中").append("...");
        
        return sb.toString();
    }
    
    @Override
    public String formatToolCallEnd(String toolName, ToolResult result) {
        StringBuilder sb = new StringBuilder();
        
        // 清除"执行中..."并显示结果
        sb.append(AnsiStyles.CLEAR_LINE);  // 清除当前行
        
        if (result.isSuccess()) {
            String outputPreview = getOutputPreview(result.getOutput());
            sb.append(AnsiStyles.DIM).append("   ↳ ").append(AnsiStyles.RESET)
              .append(AnsiStyles.GREEN).append("✓ 完成").append(AnsiStyles.RESET);
            
            if (outputPreview != null && !outputPreview.isEmpty()) {
                sb.append(AnsiStyles.DIM).append(" → ").append(AnsiStyles.RESET)
                  .append(outputPreview);
            }
        } else {
            sb.append(AnsiStyles.DIM).append("   ↳ ").append(AnsiStyles.RESET)
              .append(AnsiStyles.RED).append("✗ 失败").append(AnsiStyles.RESET)
              .append(AnsiStyles.DIM).append(": ").append(AnsiStyles.RESET)
              .append(AnsiStyles.RED).append(truncate(result.getError(), 50)).append(AnsiStyles.RESET);
        }
        
        sb.append("\n");
        return sb.toString();
    }
    
    @Override
    public String formatPermissionPrompt(Permission permission, String context) {
        StringBuilder sb = new StringBuilder();
        
        sb.append("\n")
          .append(AnsiStyles.BG_YELLOW).append(AnsiStyles.BLACK).append(" ⚠️  需要权限确认 ").append(AnsiStyles.RESET)
          .append("\n")
          .append(AnsiStyles.DIM).append("━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━").append(AnsiStyles.RESET)
          .append("\n")
          .append(AnsiStyles.BOLD).append("操作: ").append(AnsiStyles.RESET)
          .append(permission.getDescription())
          .append("\n")
          .append(AnsiStyles.BOLD).append("上下文: ").append(AnsiStyles.RESET)
          .append(AnsiStyles.DIM).append(context).append(AnsiStyles.RESET)
          .append("\n")
          .append(AnsiStyles.DIM).append("━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━").append(AnsiStyles.RESET)
          .append("\n")
          .append("是否允许? ")
          .append(AnsiStyles.BOLD).append("[y/n/session]").append(AnsiStyles.RESET)
          .append(": ");
        
        return sb.toString();
    }
    
    @Override
    public String formatError(String message) {
        return "\n" + AnsiStyles.RED + "✗ " + message + AnsiStyles.RESET + "\n";
    }
    
    @Override
    public String formatSuccess(String message) {
        return "\n" + AnsiStyles.GREEN + "✓ " + message + AnsiStyles.RESET + "\n";
    }
    
    @Override
    public String formatWarning(String message) {
        return "\n" + AnsiStyles.YELLOW + "⚠ " + message + AnsiStyles.RESET + "\n";
    }
    
    @Override
    public String formatThinking(String content) {
        return AnsiStyles.DIM + AnsiStyles.ITALIC + content + AnsiStyles.RESET;
    }
    
    // ========== 思考指示器（Thinking Indicator）==========
    
    private static final String THINKING_TEXT = "∴ Thinking";
    private static final String[] THINKING_FRAMES = {
        "∴ Thinking   ",
        "∴ Thinking.  ",
        "∴ Thinking.. ",
        "∴ Thinking..."
    };
    
    @Override
    public String formatThinkingStart() {
        return "\n" + AnsiStyles.DIM + THINKING_TEXT + "..." + AnsiStyles.RESET + "\n";
    }
    
    @Override
    public String formatThinkingEnd() {
        // 清除当前行
        return "\r" + AnsiStyles.CLEAR_LINE;
    }
    
    @Override
    public String formatThinkingContent(String content) {
        return AnsiStyles.DIM + "  ∴ " + content + AnsiStyles.RESET + "\n";
    }
    
    @Override
    public String formatProgress(int iteration, int total, long elapsedMs) {
        double seconds = elapsedMs / 1000.0;
        return "◐ [" + AnsiStyles.CYAN + (iteration + 1) + AnsiStyles.RESET + 
               "/" + total + "] " + AnsiStyles.DIM + String.format("%.1fs", seconds) + AnsiStyles.RESET + " > ";
    }
    
    @Override
    public String formatSpinner(int state) {
        return THINKING_FRAMES[state % THINKING_FRAMES.length];
    }
    
    // ========== 状态栏（Status Bar）==========
    
    @Override
    public String formatStatusBar(String model, long sessionDurationMs) {
        return "[" + AnsiStyles.CYAN + model + AnsiStyles.RESET + "] " + 
               formatDuration(sessionDurationMs);
    }
    
    // ========== Skill 格式化（新增）==========
    
    /**
     * 格式化 Skill 命中（用于硬路由）
     */
    @Override
    public String formatSkillStart(String skillName, String input) {
        StringBuilder sb = new StringBuilder();
        sb.append("\n")
          .append(AnsiStyles.BOLD).append(AnsiStyles.MAGENTA)
          .append("🎯 Skill 命中").append(AnsiStyles.RESET)
          .append(": ")
          .append(AnsiStyles.CYAN).append(skillName).append(AnsiStyles.RESET)
          .append(" - 正在执行...\n");
        return sb.toString();
    }
    
    /**
     * 格式化 Skill 调用开始
     */
    public String formatSkillCallStart(String skillName, String description) {
        StringBuilder sb = new StringBuilder();
        
        sb.append("\n")
          .append(AnsiStyles.DIM).append("┌─ ").append(AnsiStyles.RESET)
          .append(SKILL_ICON).append(" ")
          .append(AnsiStyles.BOLD).append(AnsiStyles.MAGENTA).append(skillName).append(AnsiStyles.RESET)
          .append(AnsiStyles.DIM).append(": ").append(AnsiStyles.RESET)
          .append(description)
          .append("\n")
          .append(AnsiStyles.DIM).append("└─ ").append(AnsiStyles.RESET)
          .append("执行中").append("...");
        
        return sb.toString();
    }
    
    /**
     * 格式化 Skill 调用进度阶段
     */
    @Override
    public String formatSkillProgress(String stage) {
        return AnsiStyles.CLEAR_LINE + 
               AnsiStyles.DIM + "   ↳ " + AnsiStyles.RESET +
               AnsiStyles.CYAN + stage + AnsiStyles.RESET + "...\n";
    }
    
    /**
     * 格式化 Skill 结果
     */
    @Override
    public String formatSkillEnd(com.codemind.api.skill.SkillResult result) {
        StringBuilder sb = new StringBuilder();
        if (result.isSuccess()) {
            sb.append("\n")
              .append(AnsiStyles.GREEN).append("✅ Skill 执行成功").append(AnsiStyles.RESET)
              .append("\n");
            
            String output = result.getOutput();
            if (output != null && !output.isEmpty()) {
                // 检查是否是 JSON 格式
                if (output.startsWith("{")) {
                    // JSON 输出：提取关键信息，不打印完整 JSON
                    sb.append(formatJsonOutput(output));
                } else if (output.length() > 100) {
                    // 长文本：只显示摘要
                    sb.append(AnsiStyles.DIM).append(truncate(output, 100)).append(AnsiStyles.RESET)
                      .append(AnsiStyles.DIM).append("... (共 ").append(output.length()).append(" 字符)").append(AnsiStyles.RESET);
                } else {
                    sb.append(output);
                }
            }
            sb.append("\n");
        } else {
            sb.append("\n")
              .append(AnsiStyles.RED).append("❌ Skill 执行失败").append(AnsiStyles.RESET)
              .append("\n")
              .append(AnsiStyles.RED).append(result.getError()).append(AnsiStyles.RESET)
              .append("\n");
        }
        return sb.toString();
    }
    
    /**
     * 格式化 JSON 输出，提取关键信息
     */
    private String formatJsonOutput(String json) {
        try {
            // 简单解析：提取 status 和 message
            String status = extractJsonValue(json, "status");
            String message = extractJsonValue(json, "message");
            String action = extractJsonValue(json, "action");
            
            if (action != null && "reply_to_user".equals(action)) {
                // 直接回复用户
                return message != null ? message : "";
            }
            
            if ("need_input".equals(status)) {
                return AnsiStyles.YELLOW + message + AnsiStyles.RESET;
            }
            
            if ("error".equals(status)) {
                String error = extractJsonValue(json, "error");
                return AnsiStyles.RED + "错误: " + (message != null ? message : error) + AnsiStyles.RESET;
            }
            
            if ("success".equals(status)) {
                String file = extractJsonValue(json, "file");
                int classCount = extractJsonInt(json, "classCount");
                int methodCount = extractJsonInt(json, "methodCount");
                return AnsiStyles.CYAN + "文档已生成" + AnsiStyles.RESET + 
                       (file != null ? " → " + AnsiStyles.DIM + file + AnsiStyles.RESET : "") +
                       " (" + classCount + " 类, " + methodCount + " 方法)";
            }
            
            // 默认：显示 message
            return message != null ? message : AnsiStyles.DIM + json.substring(0, Math.min(50, json.length())) + AnsiStyles.RESET;
        } catch (Exception e) {
            return AnsiStyles.DIM + truncate(json, 80) + AnsiStyles.RESET;
        }
    }
    
    /**
     * 从 JSON 字符串中提取字符串值
     */
    private String extractJsonValue(String json, String key) {
        try {
            // 简单的正则提取
            java.util.regex.Pattern pattern = java.util.regex.Pattern.compile(
                "\"" + key + "\"\\s*:\\s*\"([^\"]*)\"");
            java.util.regex.Matcher matcher = pattern.matcher(json);
            if (matcher.find()) {
                return matcher.group(1);
            }
        } catch (Exception e) {
            // 忽略
        }
        return null;
    }
    
    /**
     * 从 JSON 字符串中提取整数值
     */
    private int extractJsonInt(String json, String key) {
        try {
            java.util.regex.Pattern pattern = java.util.regex.Pattern.compile(
                "\"" + key + "\"\\s*:\\s*(\\d+)");
            java.util.regex.Matcher matcher = pattern.matcher(json);
            if (matcher.find()) {
                return Integer.parseInt(matcher.group(1));
            }
        } catch (Exception e) {
            // 忽略
        }
        return 0;
    }
    
    /**
     * 格式化 Skill 调用结束
     */
    public String formatSkillCallEnd(String skillName, boolean success, String summary) {
        StringBuilder sb = new StringBuilder();
        
        sb.append(AnsiStyles.CLEAR_LINE);
        
        if (success) {
            sb.append(AnsiStyles.DIM).append("   ↳ ").append(AnsiStyles.RESET)
              .append(AnsiStyles.GREEN).append("✓ 完成").append(AnsiStyles.RESET);
            
            if (summary != null && !summary.isEmpty()) {
                sb.append(AnsiStyles.DIM).append(" → ").append(AnsiStyles.RESET)
                  .append(truncate(summary, 80));
            }
        } else {
            sb.append(AnsiStyles.DIM).append("   ↳ ").append(AnsiStyles.RESET)
              .append(AnsiStyles.RED).append("✗ 失败").append(AnsiStyles.RESET);
        }
        
        sb.append("\n");
        return sb.toString();
    }
    
    // ========== 辅助方法 ==========
    
    /**
     * 获取工具的显示名称（根据参数推断）
     */
    private String getDisplayName(String toolName, Map<String, Object> params) {
        switch (toolName) {
            case "execute_command":
                Object cmd = params.get("command");
                if (cmd != null) {
                    String cmdStr = cmd.toString();
                    // 显示命令的关键部分（前 30 字符）
                    return truncate(cmdStr, 30);
                }
                return "执行命令";
            
            case "read_file":
                Object path = params.get("path");
                if (path != null) {
                    return path.toString();
                }
                return "读取文件";
            
            case "write_file":
                Object writePath = params.get("path");
                if (writePath != null) {
                    return writePath.toString();
                }
                return "写入文件";
            
            case "search_code":
                Object pattern = params.get("pattern");
                if (pattern != null) {
                    return "\"" + truncate(pattern.toString(), 20) + "\"";
                }
                return "搜索代码";
            
            default:
                return "";
        }
    }
    
    /**
     * 判断是否为关键参数
     */
    private boolean isKeyParam(String toolName, String paramName) {
        switch (toolName) {
            case "execute_command":
                return "command".equals(paramName) || "timeout".equals(paramName);
            case "read_file":
            case "write_file":
                return "path".equals(paramName);
            case "search_code":
                return "pattern".equals(paramName) || "path".equals(paramName);
            default:
                return true;
        }
    }
    
    /**
     * 格式化参数值
     */
    private String formatParamValue(Object value) {
        if (value == null) return "";
        String str = value.toString();
        // 去除换行，截断
        str = str.replace("\n", " ");
        return truncate(str, 40);
    }
    
    /**
     * 获取输出预览
     */
    private String getOutputPreview(String output) {
        if (output == null || output.isEmpty()) return "";

        // 对于长输出（超过3行或200字符），显示摘要
        int lineCount;
        try {
            lineCount = output.split("\n", -1).length;
        } catch (Exception e) {
            lineCount = 0;
        }

        if (lineCount > 3 || output.length() > 200) {
            return AnsiStyles.DIM + lineCount + " 行" + AnsiStyles.RESET;
        }

        // 对于短输出，显示内容预览
        return AnsiStyles.DIM + truncate(output.replace("\n", " "), 30) + AnsiStyles.RESET;
    }
    
    private String truncate(String str, int maxLength) {
        if (str == null) return "";
        if (str.length() <= maxLength) return str;
        return str.substring(0, maxLength - 3) + "...";
    }
}