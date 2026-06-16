package com.codemind.safety;

import com.codemind.safety.PermissionGate;
import com.codemind.safety.PermissionLevel;
import com.codemind.safety.spi.PermissionPrompter;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Pattern;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class PermissionGateImpl implements PermissionGate {
    private static final Logger log = LoggerFactory.getLogger(PermissionGateImpl.class);

    private final PermissionPrompter permissionPrompter;
    private final Map<String, PermissionLevel> runtimeLevels = new ConcurrentHashMap<>();
    private List<PermissionRule> rules = List.of();
    private List<Pattern> denyPatterns = List.of();

    /**
     * Bash 安全命令白名单前缀：匹配的命令自动 ALLOW，无需用户确认。
     * 减少 Agent 执行时的交互阻塞。
     */
    private List<String> bashAllowPrefixes = List.of(
        "ls", "dir", "cd", "pwd", "echo",
        "git status", "git diff", "git log", "git branch",
        "git --version", "java -version", "mvn --version",
        "python --version", "node --version",
        "type", "where", "help",
        "cat ", "more ", "less ",
        "find ", "findstr "
    );

    private static final Map<String, PermissionLevel> DEFAULT_LEVELS = Map.ofEntries(
        Map.entry("Read", PermissionLevel.ALLOW),
        Map.entry("Write", PermissionLevel.ASK),
        Map.entry("Edit", PermissionLevel.ASK),
        Map.entry("Glob", PermissionLevel.ALLOW),
        Map.entry("Grep", PermissionLevel.ALLOW),
        Map.entry("Bash", PermissionLevel.ASK),
        Map.entry("WebFetch", PermissionLevel.ALLOW),
        Map.entry("LoadSkill", PermissionLevel.ALLOW),
        Map.entry("Todo", PermissionLevel.ALLOW),
        Map.entry("Task", PermissionLevel.ALLOW)
    );

    public PermissionGateImpl(PermissionPrompter permissionPrompter) {
        this.permissionPrompter = permissionPrompter;
    }

    public record PermissionRule(String tool, PermissionLevel level) {
        public boolean matches(String toolName) {
            return "*".equals(tool) || tool.equals(toolName);
        }
    }

    public void setRules(List<PermissionRule> rules) {
        this.rules = rules != null ? rules : List.of();
    }

    public void setDenyPatterns(List<String> denyRules) {
        this.denyPatterns = denyRules != null
            ? denyRules.stream().map(Pattern::compile).toList()
            : List.of();
    }

    /**
     * 设置 Bash 安全命令白名单前缀。
     * 匹配的命令将自动 ALLOW 而无需用户确认。
     */
    public void setBashAllowPrefixes(List<String> prefixes) {
        this.bashAllowPrefixes = prefixes != null ? List.copyOf(prefixes) : List.of();
    }

    @Override
    public PermissionLevel getDefaultLevel(String toolName) {
        // 1. 运行时覆盖优先
        PermissionLevel runtime = runtimeLevels.get(toolName);
        if (runtime != null) return runtime;

        // 2. 规则匹配
        for (PermissionRule rule : rules) {
            if (rule.matches(toolName)) return rule.level();
        }

        // 3. 静态默认
        return DEFAULT_LEVELS.getOrDefault(toolName, PermissionLevel.ASK);
    }

    @Override
    public boolean requestPermission(String toolName, String context) {
        // Check deny patterns first
        for (Pattern p : denyPatterns) {
            if (p.matcher(context).find()) {
                log.warn("命令被 deny 规则阻止: [{}] 匹配模式 [{}]", context, p.pattern());
                return false;
            }
        }

        // Bash 安全命令白名单检查：匹配前缀自动放行
        if ("Bash".equals(toolName) && isBashCommandAllowed(context)) {
            log.debug("Bash 命令通过白名单自动放行: {}", context);
            return true;
        }

        if (permissionPrompter == null) return false;
        PermissionPrompter.Decision decision = permissionPrompter.prompt(toolName, context);
        if (decision == PermissionPrompter.Decision.ALLOW_SESSION) {
            runtimeLevels.put(toolName, PermissionLevel.ALLOW);
            return true;
        }
        return decision == PermissionPrompter.Decision.ALLOW;
    }

    @Override
    public void setDefaultLevel(String toolName, PermissionLevel level) {
        runtimeLevels.put(toolName, level);
    }

    /**
     * 检查 Bash 命令是否匹配安全白名单前缀。
     * 匹配的命令自动放行，无需用户确认。
     */
    private boolean isBashCommandAllowed(String context) {
        if (context == null || context.isEmpty()) return false;
        // 去掉工具名前缀，只保留命令本身
        String command = context.trim();
        int cmdStart = command.indexOf("执行: ");
        if (cmdStart >= 0) {
            command = command.substring(cmdStart + 4).trim();
        }
        for (String prefix : bashAllowPrefixes) {
            if (command.startsWith(prefix)) {
                return true;
            }
        }
        return false;
    }
}
