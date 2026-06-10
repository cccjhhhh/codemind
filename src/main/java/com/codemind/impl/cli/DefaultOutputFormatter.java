package com.codemind.impl.cli;

import com.codemind.api.cli.OutputFormatter;
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
    private static final Map<String, String> TOOL_ICONS = Map.ofEntries(
        Map.entry("Bash", "⚡"),
        Map.entry("Read", "📖"),
        Map.entry("Write", "✏️"),
        Map.entry("Edit", "🔧"),
        Map.entry("Grep", "🔍"),
        Map.entry("Glob", "📁"),
        Map.entry("WebFetch", "🌐"),
        Map.entry("LSP", "💡"),
        Map.entry("TodoWrite", "📝"),
        Map.entry("Skill", "🧩"),
        Map.entry("AskUser", "❓"),
        Map.entry("ViewCode", "🔎"),
        Map.entry("SearchWeb", "🔎"),
        Map.entry("ListDirectory", "📂"),
        Map.entry("Command", "⚙️"),
        Map.entry("Resource", "📦"),
        Map.entry("Prompt", "💬")
    );
    
    // Skill 图标
    private static final String SKILL_ICON = "🎯";

    private boolean verbose = false;

    public void setVerbose(boolean verbose) {
        this.verbose = verbose;
    }

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

            // Verbose: append first 200 chars of output
            if (verbose && result.getOutput() != null && result.getOutput().length() > 200) {
                sb.append("\n")
                  .append(AnsiStyles.DIM).append(result.getOutput().substring(0, 200)).append("...")
                  .append(AnsiStyles.RESET);
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
    public String formatPermissionPrompt(String toolName, String context) {
        StringBuilder sb = new StringBuilder();

        sb.append("\n")
          .append(AnsiStyles.BG_YELLOW).append(AnsiStyles.BLACK).append(" ⚠️  需要权限确认 ").append(AnsiStyles.RESET)
          .append("\n")
          .append(AnsiStyles.DIM).append("━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━").append(AnsiStyles.RESET)
          .append("\n")
          .append(AnsiStyles.BOLD).append("操作: ").append(AnsiStyles.RESET)
          .append("工具: ").append(toolName)
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
    
    private static final String[] THINKING_FRAMES = {
        "∴ Thinking   ",
        "∴ Thinking.  ",
        "∴ Thinking.. ",
        "∴ Thinking..."
    };
    
    @Override
    public String formatThinkingStart() {
        // 不显示 Thinking 指示器
        return "";
    }
    
    @Override
    public String formatThinkingEnd() {
        // 不显示
        return "";
    }
    
    @Override
    public String formatThinkingContent(String content) {
        // 不显示思考内容
        return "";
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
     * 格式化 Skill 调用进度阶段
     */
    @Override
    public String formatSkillProgress(String stage) {
        return AnsiStyles.CLEAR_LINE + 
               AnsiStyles.DIM + "   ↳ " + AnsiStyles.RESET +
               AnsiStyles.CYAN + stage + AnsiStyles.RESET + "...\n";
    }
    
    /**
     * 格式化 Skill 调用结束
     */
    @Override
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
        if (toolName == null) return "Unknown";
        return switch (toolName) {
            case "Bash" -> {
                Object cmd = params.get("command");
                if (cmd != null) {
                    yield truncate(cmd.toString(), 30);
                }
                yield "执行命令";
            }
            case "Read" -> {
                Object path = params.get("filePath");
                if (path != null) {
                    yield path.toString();
                }
                yield "读取文件";
            }
            case "Write" -> {
                Object writePath = params.get("filePath");
                if (writePath != null) {
                    yield writePath.toString();
                }
                yield "写入文件";
            }
            case "Edit" -> {
                Object editPath = params.get("filePath");
                if (editPath != null) {
                    yield editPath.toString();
                }
                yield "编辑文件";
            }
            case "Grep" -> {
                Object pattern = params.get("pattern");
                if (pattern != null) {
                    yield "\"" + truncate(pattern.toString(), 20) + "\"";
                }
                yield "搜索代码";
            }
            case "Glob" -> {
                Object globPattern = params.get("pattern");
                if (globPattern != null) {
                    yield globPattern.toString();
                }
                yield "查找文件";
            }
            case "WebFetch" -> {
                Object url = params.get("url");
                if (url != null) {
                    yield url.toString();
                }
                yield "获取网页";
            }
            case "LSP" -> {
                Object lspPath = params.get("filePath");
                if (lspPath != null) {
                    yield lspPath.toString();
                }
                yield "LSP 诊断";
            }
            case "TodoWrite" -> "任务管理";
            case "Skill" -> {
                Object skillName = params.get("name");
                if (skillName != null) {
                    yield skillName.toString();
                }
                yield "加载技能";
            }
            case "AskUser" -> {
                Object question = params.get("question");
                if (question != null) {
                    yield truncate(question.toString(), 30);
                }
                yield "询问用户";
            }
            case "ViewCode" -> {
                Object viewPath = params.get("filePath");
                if (viewPath != null) {
                    yield viewPath.toString();
                }
                yield "查看代码";
            }
            case "SearchWeb" -> {
                Object query = params.get("query");
                if (query != null) {
                    yield truncate(query.toString(), 30);
                }
                yield "搜索网页";
            }
            case "ListDirectory" -> {
                Object dirPath = params.get("path");
                if (dirPath != null) {
                    yield dirPath.toString();
                }
                yield "列出目录";
            }
            case "Command" -> {
                Object mcpCmd = params.get("command");
                if (mcpCmd != null) {
                    yield mcpCmd.toString();
                }
                yield "执行命令";
            }
            case "Resource" -> {
                Object uri = params.get("uri");
                if (uri != null) {
                    yield uri.toString();
                }
                yield "获取资源";
            }
            case "Prompt" -> {
                Object promptName = params.get("name");
                if (promptName != null) {
                    yield promptName.toString();
                }
                yield "使用提示";
            }
            default -> "";
        };
    }
    
    /**
     * 判断是否为关键参数
     */
    private boolean isKeyParam(String toolName, String paramName) {
        if (toolName == null || paramName == null) return false;
        return switch (toolName) {
            case "Bash" -> "command".equals(paramName);
            case "Read" -> "filePath".equals(paramName);
            case "Write" -> "filePath".equals(paramName);
            case "Edit" -> "filePath".equals(paramName);
            case "Grep" -> "pattern".equals(paramName) || "query".equals(paramName);
            case "Glob" -> "pattern".equals(paramName);
            case "WebFetch" -> "url".equals(paramName);
            case "LSP" -> "filePath".equals(paramName);
            case "TodoWrite" -> false;
            case "Skill" -> "name".equals(paramName);
            case "AskUser" -> "question".equals(paramName);
            case "ViewCode" -> "filePath".equals(paramName);
            case "SearchWeb" -> "query".equals(paramName);
            case "ListDirectory" -> "path".equals(paramName);
            case "Command" -> "command".equals(paramName);
            case "Resource" -> "uri".equals(paramName);
            case "Prompt" -> "name".equals(paramName);
            default -> false;
        };
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