package com.codemind.agent;

import com.codemind.session.SessionContext;
import com.codemind.tool.ToolRegistry;
import com.codemind.skill.SkillDefinition;
import com.codemind.skill.SkillEntry;
import com.codemind.skill.SkillRegistry;

import java.util.List;

public class SystemPromptBuilder {

    private final ToolRegistry toolRegistry;
    private final SkillRegistry skillRegistry;

    public SystemPromptBuilder(ToolRegistry toolRegistry, SkillRegistry skillRegistry) {
        this.toolRegistry = toolRegistry;
        this.skillRegistry = skillRegistry;
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
        
        // Detect actual shell available
        String shellInfo;
        String shellInstructions;
        if (detectOS().contains("Windows")) {
            if (isPowerShellAvailable()) {
                shellInfo = "PowerShell";
                shellInstructions = "Use PowerShell commands. Examples:\n" +
                    "- List files: `Get-ChildItem` or `ls`\n" +
                    "- Recursive list: `Get-ChildItem -Recurse -Name`\n" +
                    "- Read file: `Get-Content file.txt`\n" +
                    "- Search text: `Select-String -Path *.txt -Pattern \"pattern\"`\n" +
                    "- Filter: `Where-Object { $_.Name -like \"*.java\" }`\n" +
                    "Do NOT use cmd.exe commands like `dir`, `type`, `findstr`.";
            } else {
                shellInfo = "cmd.exe";
                shellInstructions = "Use cmd.exe commands. Examples:\n" +
                    "- List files: `dir`\n" +
                    "- Read file: `type file.txt`\n" +
                    "- Search text: `findstr /s \"pattern\" *.txt`\n" +
                    "Do NOT use Unix commands like `ls`, `cat`, `grep`.";
            }
        } else {
            shellInfo = "bash";
            shellInstructions = "Use bash/Unix commands. Examples:\n" +
                "- List files: `ls`\n" +
                "- Read file: `cat file.txt`\n" +
                "- Search text: `grep -r \"pattern\" .`\n" +
                "- Recursive find: `find . -name \"*.java\"`";
        }
        
        sb.append("- **Shell**: ").append(shellInfo).append("\n\n");
        sb.append(shellInstructions).append("\n\n");
        sb.append("All commands execute in the working directory by default. ");
        sb.append("Do not guess paths. Use the Bash tool's `cwd` parameter if needed.\n\n");

        // 3. AVAILABLE TOOLS
        sb.append("## Available Tools\n\n");
        for (var def : toolRegistry.getAllDefinitions()) {
            sb.append("- **").append(def.getFunction().getName()).append("**");
            sb.append(": ").append(def.getFunction().getDescription().split("\n")[0]).append("\n");
        }
        sb.append("\n");

        // 4. AVAILABLE SKILLS (summaries only — full content loaded on activation)
        List<SkillEntry> allSkills = skillRegistry.listAll();
        if (!allSkills.isEmpty()) {
            sb.append("## Available Skills\n\n");
            for (SkillEntry entry : allSkills) {
                String desc = entry.metadata().description();
                // Truncate long descriptions to keep system prompt lean
                if (desc.length() > 150) {
                    desc = desc.substring(0, 147) + "...";
                }
                sb.append("- **").append(entry.name()).append("**");
                sb.append(": ").append(desc).append("\n");
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

    private boolean isPowerShellAvailable() {
        try {
            ProcessBuilder pb = new ProcessBuilder("powershell", "-Command", "echo test");
            pb.redirectErrorStream(true);
            Process process = pb.start();
            boolean finished = process.waitFor(5, java.util.concurrent.TimeUnit.SECONDS);
            if (!finished) {
                process.destroyForcibly();
                return false;
            }
            return process.exitValue() == 0;
        } catch (Exception e) {
            return false;
        }
    }
}
