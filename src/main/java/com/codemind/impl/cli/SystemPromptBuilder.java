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

        // 1. IDENTITY
        sb.append("You are CodeMind, a coding agent at ")
          .append(context.getWorkingDirectory()).append(".\n\n");

        // 2. ENVIRONMENT
        sb.append("## Environment\n\n");
        sb.append("- **Working Directory**: `").append(context.getWorkingDirectory()).append("`\n");
        sb.append("- **OS**: ").append(detectOS()).append("\n");
        sb.append("- **Shell**: ").append(detectOS().contains("Windows") ? "cmd.exe" : "bash").append("\n\n");
        sb.append("All Bash commands execute in the working directory by default. ");
        sb.append("Do not guess paths. Use the Bash tool's `cwd` parameter if needed.\n\n");

        // 3. AVAILABLE TOOLS
        sb.append("## Available Tools\n\n");
        for (var def : toolRegistry.getAllDefinitions()) {
            sb.append("- **").append(def.getFunction().getName()).append("**");
            sb.append(": ").append(def.getFunction().getDescription().split("\n")[0]).append("\n");
        }
        sb.append("\n");

        // 4. AVAILABLE SKILLS (summaries only — full content loaded on activation)
        sb.append("## Available Skills\n\n");
        if (skills.isEmpty()) {
            sb.append("No skills currently loaded.\n\n");
        } else {
            for (SkillDefinition skill : skills) {
                sb.append("- **").append(skill.getName()).append("**");
                sb.append(": ").append(skill.getDescription()).append("\n");
            }
            sb.append("\nSkills activate automatically when the system detects a matching request. ");
            sb.append("You can also call LoadSkill to manually load a skill.\n\n");
        }

        // 5. ACTIVE SKILL (conditionally injected)
        if (context.hasActiveSkill()) {
            SkillDefinition active = context.getActiveSkill();
            sb.append("══════════════════════════════════════════════\n");
            sb.append("## Active Skill: ").append(active.getName()).append("\n");
            sb.append("══════════════════════════════════════════════\n\n");
            sb.append(active.getFullContent()).append("\n\n");
            sb.append("══════════════════════════════════════════════\n");
            sb.append("Follow the skill instructions above strictly.\n");
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
