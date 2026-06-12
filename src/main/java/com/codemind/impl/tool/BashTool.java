package com.codemind.impl.tool;

import com.codemind.api.tool.Tool;
import com.codemind.api.tool.ToolResult;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.BufferedReader;
import java.io.File;
import java.io.InputStreamReader;
import java.nio.charset.Charset;
import java.util.Map;
import java.util.Set;

public class BashTool implements Tool {

    private static final Logger log = LoggerFactory.getLogger(BashTool.class);
    private static final ObjectMapper JSON = new ObjectMapper();
    private static final int DEFAULT_TIMEOUT_SECONDS = 30;

    /** Unix 命令 → Windows 等效命令的映射 */
    private static final Map<String, String> WIN_CMD_ALIASES = Map.ofEntries(
        Map.entry("head", "findstr /n"),
        Map.entry("tail", "findstr /n"),
        Map.entry("grep", "findstr"),
        Map.entry("cat", "type"),
        Map.entry("less", "more"),
        Map.entry("more", "more"),
        Map.entry("cp", "copy"),
        Map.entry("mv", "move"),
        Map.entry("rm", "del"),
        Map.entry("mkdir", "mkdir"),
        Map.entry("touch", "copy /b nul+"),
        Map.entry("diff", "fc"),
        Map.entry("sort", "sort"),
        Map.entry("uniq", "sort /unique"),
        Map.entry("wc", "findstr /c:")
    );

    /** 已知 Windows 上不存在的命令集合（用于给出提示） */
    private static final Set<String> UNIX_ONLY_COMMANDS = Set.of(
        "sudo", "chmod", "chown", "ln", "ps", "kill", "whoami",
        "which", "curl", "wget", "unzip", "tar", "gzip"
    );

    @Override
    public String getName() { return "Bash"; }

    @Override
    public String getDescription() {
        String os = isWindows() ? "Windows (cmd.exe)" : "Unix (sh)";
        return "执行 shell 命令并返回输出结果。\n" +
               "当前环境: " + os + "\n" +
               (isWindows()
                 ? "注意：Windows 环境部分 Unix 命令不可用（如 grep→findstr, head→findstr /n, cat→type）。\n"
                 : "") +
               "命令会在当前工作目录下执行。";
    }

    @Override
    public java.util.Optional<String> getDeprecatedName() {
        return java.util.Optional.of("execute_command");
    }

    @Override
    public JsonNode getInputSchema() {
        ObjectNode schema = JSON.createObjectNode();
        schema.put("type", "object");
        ObjectNode properties = schema.putObject("properties");
        ObjectNode cmdProp = properties.putObject("command");
        cmdProp.put("type", "string");
        cmdProp.put("description", "要执行的命令");
        ObjectNode timeoutProp = properties.putObject("timeout");
        timeoutProp.put("type", "integer");
        timeoutProp.put("description", "超时时间（秒）");
        ObjectNode cwdProp = properties.putObject("cwd");
        cwdProp.put("type", "string");
        cwdProp.put("description", "工作目录（可选）");
        schema.putArray("required").add("command");
        return schema;
    }

    @Override
    public ToolResult execute(Map<String, Object> params) {
        try {
            String command = (String) params.get("command");
            if (command == null || command.isEmpty()) {
                return ToolResult.failure("参数 'command' 是必需的");
            }

            // 安全检查已移入 SafetyPreHook
            Integer timeout = (Integer) params.get("timeout");
            int timeoutSeconds = timeout != null ? timeout : DEFAULT_TIMEOUT_SECONDS;

            String cwd = (String) params.get("cwd");
            File workingDir = null;
            if (cwd != null && !cwd.isEmpty()) {
                workingDir = new File(cwd);
                log.debug("BashTool: cwd={}", workingDir.getAbsolutePath());
            }

            // 跨平台命令适配：Windows 上自动翻译常见 Unix 命令
            String adaptedCommand = isWindows() ? adaptCommandForWindows(command) : command;
            log.debug("Executing command: {} (adapted: {})", command, adaptedCommand);

            ProcessBuilder pb = new ProcessBuilder();
            if (isWindows()) {
                pb.command("cmd", "/c", adaptedCommand);
            } else {
                pb.command("sh", "-c", adaptedCommand);
            }
            if (workingDir != null) pb.directory(workingDir);
            pb.redirectErrorStream(true);

            Process process = pb.start();

            StringBuilder output = new StringBuilder();
            Charset charset = Charset.defaultCharset();
            try (BufferedReader reader = new BufferedReader(
                    new InputStreamReader(process.getInputStream(), charset))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    output.append(line).append("\n");
                }
            }

            boolean finished = process.waitFor(timeoutSeconds, java.util.concurrent.TimeUnit.SECONDS);
            if (!finished) {
                process.destroyForcibly();
                return ToolResult.failure("命令执行超时");
            }

            int exitCode = process.exitValue();
            if (exitCode != 0) {
                return ToolResult.failure("命令执行失败（退出码: " + exitCode + "）\n输出: " + output);
            }

            log.debug("Command output: {}", output.toString().trim());
            return ToolResult.success(output.toString());

        } catch (Exception e) {
            return ToolResult.failure("命令执行异常: " + e.getMessage());
        }
    }

    private static boolean isWindows() {
        return System.getProperty("os.name").toLowerCase().contains("win");
    }

    /**
     * 将 Unix 风格命令转换为 Windows 等效命令。
     * 只翻译简单命令（第一个词匹配已知别名），复杂管线不处理。
     */
    private static String adaptCommandForWindows(String command) {
        if (command == null || command.isEmpty()) return command;
        String trimmed = command.trim();
        // 从管线中提取第一个命令
        String firstCmd = trimmed.split("\\||;|&")[0].trim().split("\\s+")[0];
        String winCmd = WIN_CMD_ALIASES.get(firstCmd);
        if (winCmd != null) {
            String rest = trimmed.substring(firstCmd.length());
            log.debug("命令翻译: '{}' → '{}'", firstCmd, winCmd);
            return winCmd + rest;
        }
        // 检查是否使用了已知的 Unix-only 命令
        for (String unixCmd : UNIX_ONLY_COMMANDS) {
            if (trimmed.startsWith(unixCmd + " ") || trimmed.equals(unixCmd)) {
                log.warn("命令 '{}' 在 Windows 上不可用", unixCmd);
            }
        }
        return trimmed;
    }
}
