package com.codemind.impl.cli;

import com.codemind.api.session.SessionContext;
import com.codemind.api.tool.ToolRegistry;
import com.codemind.impl.skill.SkillDefinition;

import java.util.List;

public class SystemPromptBuilder {

    private final ToolRegistry toolRegistry;
    private final List<SkillDefinition> skills;

    public SystemPromptBuilder(ToolRegistry toolRegistry, List<SkillDefinition> skills) {
        this.toolRegistry = toolRegistry;
        this.skills = skills;
    }

    public String build(SessionContext context) {
        StringBuilder sb = new StringBuilder();

        sb.append("你是一个安全的 AI 编程助手 CodeMind。\n\n");

        // 0. Environment context — critical for avoiding path guessing
        sb.append("## 运行环境\n\n");
        sb.append("- **工作目录**: `").append(context.getWorkingDirectory()).append("`\n");
        sb.append("- **操作系统**: ").append(detectOS()).append("\n");
        sb.append("- **Shell**: ").append(detectOS().contains("Windows") ? "cmd.exe (Windows)\n" : "sh/bash (Unix)\n");
        sb.append("\n");
        sb.append("**重要**: 所有 Bash 命令默认在工作目录下执行。\n");
        sb.append("不要猜测路径，不要使用 `cd` 切换目录。\n");
        sb.append("如果需要在不同目录执行命令，使用 Bash 工具的 `cwd` 参数。\n\n");

        // 1. Tool list
        sb.append("## 可用工具\n\n");
        for (var def : toolRegistry.getAllDefinitions()) {
            sb.append("- **").append(def.getFunction().getName()).append("**");
            sb.append(": ").append(def.getFunction().getDescription().split("\n")[0]).append("\n");
        }
        sb.append("\n");

        // 2. Skill summaries
        sb.append("## 可用技能\n\n");
        for (SkillDefinition skill : skills) {
            sb.append("- **").append(skill.getName()).append("**");
            sb.append(": ").append(skill.getDescription()).append("\n");
        }
        sb.append("当用户请求匹配某个技能时，系统会自动激活对应技能并注入完整指令。\n");
        sb.append("你只需要按照注入的指令逐步执行即可。\n\n");

        // 3. Active skill full content
        if (context.hasActiveSkill()) {
            SkillDefinition active = context.getActiveSkill();
            sb.append("══════════════════════════════════════════════\n");
            sb.append("## 当前激活技能：").append(active.getName()).append("\n");
            sb.append("══════════════════════════════════════════════\n\n");
            sb.append(active.getFullContent()).append("\n\n");
            sb.append("══════════════════════════════════════════════\n");
            sb.append("请严格按照以上技能指令执行。完成后用户会告诉你要做什么。\n");
            sb.append("══════════════════════════════════════════════\n");
        }

        return sb.toString();
    }

    private String detectOS() {
        String os = System.getProperty("os.name").toLowerCase();
        if (os.contains("win")) return "Windows";
        if (os.contains("mac")) return "macOS";
        if (os.contains("nix") || os.contains("nux")) return "Linux";
        return os;
    }
}
