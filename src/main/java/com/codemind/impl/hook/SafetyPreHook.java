package com.codemind.impl.hook;

import com.codemind.api.tool.ToolHook;
import com.codemind.api.tool.ToolResult;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;

public class SafetyPreHook implements ToolHook {

    private static final List<Pattern> DANGEROUS_PATTERNS = List.of(
        Pattern.compile("rm\\s+-rf"),
        Pattern.compile("del\\s+/[fqs]"),
        Pattern.compile("format\\s+"),
        Pattern.compile("fdisk"),
        Pattern.compile("mkfs"),
        Pattern.compile("dd\\s+if="),
        Pattern.compile("shutdown"),
        Pattern.compile("reboot"),
        Pattern.compile("init\\s+0"),
        Pattern.compile("kill\\s+-9"),
        Pattern.compile("\\|\\s*sh\\s")
    );

    @Override
    public void preExecute(String toolName, Map<String, Object> args) {
        if (!"Bash".equals(toolName)) return;

        String command = (String) args.get("command");
        if (command == null) return;

        for (Pattern p : DANGEROUS_PATTERNS) {
            if (p.matcher(command).find()) {
                throw new SecurityException(
                    "命令被安全策略拒绝：检测到危险命令模式 '" + p.pattern() + "'"
                );
            }
        }
    }

    @Override
    public void postExecute(String toolName, ToolResult result, long elapsedMs) {}
}
