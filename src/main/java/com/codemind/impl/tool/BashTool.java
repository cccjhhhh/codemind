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
        String os;
        String shellType;
        String examples;
        
        if (isWindows()) {
            if (isPowerShellAvailable()) {
                os = "Windows";
                shellType = "PowerShell";
                examples = "支持的命令:\n" +
                    "- Get-ChildItem / ls: 列出文件\n" +
                    "- Get-Content: 读取文件\n" +
                    "- Select-String: 搜索文本\n" +
                    "- Where-Object: 过滤对象\n" +
                    "- ForEach-Object: 循环处理\n" +
                    "不要使用 cmd.exe 命令 (dir, type, findstr)。";
            } else {
                os = "Windows";
                shellType = "cmd.exe";
                examples = "支持的命令:\n" +
                    "- dir: 列出文件\n" +
                    "- type: 读取文件\n" +
                    "- findstr: 搜索文本\n" +
                    "不要使用 Unix 命令 (ls, cat, grep)。";
            }
        } else {
            os = "Unix/Linux";
            shellType = "bash";
            examples = "支持的命令:\n" +
                "- ls: 列出文件\n" +
                "- cat: 读取文件\n" +
                "- grep: 搜索文本\n" +
                "- find: 查找文件\n" +
                "- pipe (|): 管道命令";
        }
        
        return "执行 shell 命令并返回输出结果。\n" +
               "当前环境: " + os + " (" + shellType + ")\n" +
               examples + "\n" +
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
            String adaptedCommand;
            boolean usePowerShell = false;
            if (isWindows()) {
                if (isPowerShellAvailable()) {
                    adaptedCommand = translateToPowerShell(command);
                    usePowerShell = true;
                    log.debug("Using PowerShell for command translation");
                } else {
                    adaptedCommand = adaptCommandForWindows(command);
                    log.warn("PowerShell not available, falling back to cmd.exe with limited Unix support");
                }
            } else {
                adaptedCommand = command;
            }
            log.debug("Executing command: {} (adapted: {})", command, adaptedCommand);

            ProcessBuilder pb = new ProcessBuilder();
            if (isWindows()) {
                if (usePowerShell) {
                    pb.command("powershell", "-Command", adaptedCommand);
                } else {
                    pb.command("cmd", "/c", adaptedCommand);
                }
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
     * 检测 PowerShell 是否可用。
     */
    private static boolean isPowerShellAvailable() {
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
            log.debug("PowerShell detection failed: {}", e.getMessage());
            return false;
        }
    }

    /**
     * 将 Unix 风格命令转换为 PowerShell 等效命令。
     */
    private static String translateToPowerShell(String command) {
        if (command == null || command.isEmpty()) return command;
        String trimmed = command.trim();

        // head -N file -> Get-Content file | Select-Object -First N
        if (trimmed.matches("^head\\s+-(\\d+)\\s+.+")) {
            java.util.regex.Matcher m = java.util.regex.Pattern.compile("^head\\s+-(\\d+)\\s+(.+)").matcher(trimmed);
            if (m.matches()) {
                int n = Integer.parseInt(m.group(1));
                String file = m.group(2);
                return "Get-Content " + file + " | Select-Object -First " + n;
            }
        }
        // head file -> Get-Content file (default -First 10)
        if (trimmed.matches("^head\\s+.+") && !trimmed.matches("^head\\s+-\\d+.+")) {
            String file = trimmed.substring(4).trim();
            return "Get-Content " + file + " | Select-Object -First 10";
        }

        // grep pattern file -> Get-Content file | Select-String pattern
        if (trimmed.matches("^grep\\s+.+\\s+.+")) {
            java.util.regex.Matcher m = java.util.regex.Pattern.compile("^grep\\s+(-i\\s+)?(.+?)\\s+(.+)").matcher(trimmed);
            if (m.matches()) {
                boolean caseInsensitive = m.group(1) != null;
                String pattern = m.group(2);
                String file = m.group(3);
                String psPattern = caseInsensitive ? ("-CaseInsensitive") : "";
                return "Get-Content " + file + " | Select-String " + psPattern + " '" + pattern + "'";
            }
        }

        // cat file -> Get-Content file
        if (trimmed.matches("^cat\\s+.+")) {
            String file = trimmed.substring(3).trim();
            return "Get-Content " + file;
        }

        // tail -N file -> Get-Content file | Select-Object -Last N
        if (trimmed.matches("^tail\\s+-\\d+\\s+.+")) {
            java.util.regex.Matcher m = java.util.regex.Pattern.compile("^tail\\s+-(\\d+)\\s+(.+)").matcher(trimmed);
            if (m.matches()) {
                int n = Integer.parseInt(m.group(1));
                String file = m.group(2);
                return "Get-Content " + file + " | Select-Object -Last " + n;
            }
        }
        // tail file -> Get-Content file (default -Last 10)
        if (trimmed.matches("^tail\\s+.+") && !trimmed.matches("^tail\\s+-\\d+.+")) {
            String file = trimmed.substring(4).trim();
            return "Get-Content " + file + " | Select-Object -Last 10";
        }

        // less file -> Get-Content file | Out-Host -Paging
        if (trimmed.matches("^less\\s+.+")) {
            String file = trimmed.substring(4).trim();
            return "Get-Content " + file + " | Out-Host -Paging";
        }

        // more file -> Get-Content file | Out-Host -Paging
        if (trimmed.matches("^more\\s+.+")) {
            String file = trimmed.substring(4).trim();
            return "Get-Content " + file + " | Out-Host -Paging";
        }

        // wc file -> (Get-Content file | Measure-Object -Line).Lines
        if (trimmed.matches("^wc\\s+.+")) {
            String file = trimmed.substring(2).trim();
            return "(Get-Content " + file + " | Measure-Object -Line).Lines";
        }

        // sort file -> Get-Content file | Sort-Object
        if (trimmed.matches("^sort\\s+.+")) {
            String file = trimmed.substring(4).trim();
            return "Get-Content " + file + " | Sort-Object";
        }

        // diff file1 file2 -> Compare-Object (Get-Content file1) (Get-Content file2)
        if (trimmed.matches("^diff\\s+.+\\s+.+")) {
            java.util.regex.Matcher m = java.util.regex.Pattern.compile("^diff\\s+(.+?)\\s+(.+)").matcher(trimmed);
            if (m.matches()) {
                String file1 = m.group(1);
                String file2 = m.group(2);
                return "Compare-Object (Get-Content " + file1 + ") (Get-Content " + file2 + ")";
            }
        }

        // cp source dest -> Copy-Item source dest
        if (trimmed.matches("^cp\\s+.+\\s+.+")) {
            java.util.regex.Matcher m = java.util.regex.Pattern.compile("^cp\\s+(.+?)\\s+(.+)").matcher(trimmed);
            if (m.matches()) {
                String source = m.group(1);
                String dest = m.group(2);
                return "Copy-Item " + source + " " + dest;
            }
        }

        // mv source dest -> Move-Item source dest
        if (trimmed.matches("^mv\\s+.+\\s+.+")) {
            java.util.regex.Matcher m = java.util.regex.Pattern.compile("^mv\\s+(.+?)\\s+(.+)").matcher(trimmed);
            if (m.matches()) {
                String source = m.group(1);
                String dest = m.group(2);
                return "Move-Item " + source + " " + dest;
            }
        }

        // rm file -> Remove-Item file
        if (trimmed.matches("^rm\\s+.+")) {
            String file = trimmed.substring(2).trim();
            return "Remove-Item " + file;
        }

        // mkdir dir -> New-Item -ItemType Directory -Path dir
        if (trimmed.matches("^mkdir\\s+.+")) {
            String dir = trimmed.substring(6).trim();
            return "New-Item -ItemType Directory -Path " + dir;
        }

        // touch file -> New-Item -ItemType File -Path file (if not exists)
        if (trimmed.matches("^touch\\s+.+")) {
            String file = trimmed.substring(5).trim();
            return "if (!(Test-Path " + file + ")) { New-Item -ItemType File -Path " + file + " }";
        }

        // ls -> Get-ChildItem
        if (trimmed.equals("ls") || trimmed.startsWith("ls ")) {
            String rest = trimmed.length() > 2 ? trimmed.substring(2) : "";
            return "Get-ChildItem" + rest;
        }

        // pwd -> Get-Location
        if (trimmed.equals("pwd")) {
            return "Get-Location";
        }

        // echo -> Write-Output
        if (trimmed.startsWith("echo ")) {
            String rest = trimmed.substring(4).trim();
            return "Write-Output " + rest;
        }

        // dir /s /b -> Get-ChildItem -Recurse -Name (list files recursively)
        if (trimmed.matches("^dir\\s+.*/s.*") || trimmed.equals("dir")) {
            // Extract options and path
            String options = "";
            String path = ".";
            
            // Parse dir command options
            if (trimmed.contains("/s")) {
                options += " -Recurse";
            }
            if (trimmed.contains("/b")) {
                options += " -Name";
            }
            if (trimmed.contains("/a:-d")) {
                options += " -File";  // Only files, not directories
            }
            
            // Extract path (last argument that doesn't start with /)
            String[] parts = trimmed.split("\\s+");
            for (int i = 1; i < parts.length; i++) {
                if (!parts[i].startsWith("/")) {
                    path = parts[i];
                }
            }
            
            return "Get-ChildItem" + options + " " + path;
        }

        // findstr /v pattern -> Select-String -NotMatch pattern
        if (trimmed.contains("findstr")) {
            // This is a cmd.exe command - better to run via cmd.exe
            log.debug("Detected findstr command, should run via cmd.exe");
            return "cmd.exe /c \"" + trimmed + "\"";
        }

        // Check for cmd.exe-specific syntax (2>nul, etc.)
        if (trimmed.contains("2>nul") || trimmed.contains("1>") || trimmed.contains("2>")) {
            log.debug("Detected cmd.exe redirect syntax, should run via cmd.exe");
            return "cmd.exe /c \"" + trimmed + "\"";
        }

        // Unknown command - return as-is for PowerShell to handle
        log.debug("No PowerShell translation for command: {}", trimmed);
        return trimmed;
    }

    /**
     * 将 Unix 风格命令转换为 Windows 等效命令（使用 cmd.exe 风格）。
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
