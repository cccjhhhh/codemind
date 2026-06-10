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
import java.nio.charset.StandardCharsets;
import java.util.Map;

public class BashTool implements Tool {

    private static final Logger log = LoggerFactory.getLogger(BashTool.class);
    private static final ObjectMapper JSON = new ObjectMapper();
    private static final int DEFAULT_TIMEOUT_SECONDS = 30;

    @Override
    public String getName() { return "Bash"; }

    @Override
    public String getDescription() {
        return "执行 shell 命令并返回输出结果。\n" +
               "注意：命令会在工作目录下执行。\n" +
               "Windows 系统使用 'cmd /c'，Unix 系统使用 'sh -c'。";
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

            ProcessBuilder pb = new ProcessBuilder();
            if (isWindows()) {
                pb.command("cmd", "/c", command);
            } else {
                pb.command("sh", "-c", command);
            }
            if (workingDir != null) pb.directory(workingDir);
            pb.redirectErrorStream(true);

            log.debug("Executing command: {}", command);
            Process process = pb.start();

            StringBuilder output = new StringBuilder();
            Charset charset = isWindows()
                ? Charset.forName(System.getProperty("sun.jnu.encoding", "GBK"))
                : StandardCharsets.UTF_8;
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
}
