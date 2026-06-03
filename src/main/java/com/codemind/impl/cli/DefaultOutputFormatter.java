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
    
    // ========== Skill 格式化（新增）==========
    
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
    public String formatSkillProgress(String stage) {
        return AnsiStyles.CLEAR_LINE + 
               AnsiStyles.DIM + "   ↳ " + AnsiStyles.RESET +
               AnsiStyles.CYAN + stage + AnsiStyles.RESET + "...";
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
        
        // 对于长输出，显示行数或长度
        String[] lines = output.split("\n");
        if (lines.length > 5) {
            return AnsiStyles.DIM + lines.length + " 行输出" + AnsiStyles.RESET;
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