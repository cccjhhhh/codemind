package com.codemind.impl.tool;

import com.codemind.api.safety.PermissionLevel;
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
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * 命令执行工具
 *
 * 学习要点：
 * - 进程执行与结果收集
 * - 安全沙箱设计
 * - 超时处理
 * - 工作目录设置
 */
public class BashTool implements Tool {

    private static final Logger log = LoggerFactory.getLogger(BashTool.class);
    private static final ObjectMapper JSON = new ObjectMapper();
    private static final int DEFAULT_TIMEOUT_SECONDS = 30;
    
    // 命令白名单（支持完整命令和模式匹配）
    // 模式以 ^ 开头表示正则表达式
    private static final Set<String> ALLOWED_COMMANDS = Set.of(
        // Git 命令
        "git status",
        "git diff",
        "git diff --staged",
        "git log",
        "git log --oneline",
        "git branch",
        "git branch -a",
        "git show",
        "git remote -v",
        "git config --list",
        "git ls-files",
        // 构建工具
        "mvn",
        "mvn clean",
        "mvn compile",
        "mvn test",
        "mvn package",
        "mvn install",
        "mvn verify",
        "gradle",
        "gradle build",
        "gradle test",
        "gradle assemble",
        // 包管理
        "npm",
        "npm install",
        "npm run",
        "npm test",
        "npm build",
        "pip",
        "pip install",
        "pip list",
        "pip show",
        // 文件操作
        "ls",
        "ls -la",
        "dir",
        "dir /a",
        "type",
        "cat",
        "head",
        "tail",
        "wc",
        "find",
        "grep",
        "rg",
        "fd",
        // 系统信息
        "hostname",
        "whoami",
        "pwd",
        "cd",
        "echo",
        "date",
        "which",
        "where",
        "java -version",
        "javac -version",
        "node -v",
        "npm -v",
        "python --version",
        "go version"
    );
    
    // 允许的正则表达式模式
    private static final Set<Pattern> ALLOWED_PATTERNS = Set.of(
        // Git 命令变体（允许带参数）
        Pattern.compile("^git\\s+.*"),
        // mvn/gradle 变体
        Pattern.compile("^mvn\\s+.*"),
        Pattern.compile("^gradle\\s+.*"),
        // npm 命令变体
        Pattern.compile("^npm\\s+.*"),
        // pip 命令变体
        Pattern.compile("^pip\\s+.*"),
        // ls/dir 变体
        Pattern.compile("^(ls|dir)\\s+.*"),
        // find/grep 变体
        Pattern.compile("^(find|grep|rg|fd)\\s+.*"),
        // Java/Node 版本查询
        Pattern.compile("^.+\\s+-version\\s*$")
    );
    
    // 危险命令黑名单（这些命令直接拒绝）
    private static final Set<Pattern> DANGEROUS_PATTERNS = Set.of(
        Pattern.compile("rm\\s+-rf"),
        Pattern.compile("del\\s+/[fqs]"),
        Pattern.compile("format\\s+"),
        Pattern.compile("fdisk"),
        Pattern.compile("mkfs"),
        Pattern.compile("dd\\s+if="),
        Pattern.compile(">\\s*/dev/"),
        Pattern.compile(";\\s*rm\\s"),
        Pattern.compile("&&\\s*rm\\s"),
        Pattern.compile("\\|\\s*rm\\s"),
        Pattern.compile("shutdown"),
        Pattern.compile("reboot"),
        Pattern.compile("init\\s+0"),
        Pattern.compile("kill\\s+-9"),
        Pattern.compile("^curl\\s+.*\\|\\s*sh"),
        Pattern.compile("^wget\\s+.*\\|\\s*sh"),
        Pattern.compile("^python\\s+.*\\|\\s*sh"),
        Pattern.compile("^perl\\s+.*\\|\\s*sh"),
        Pattern.compile("^ruby\\s+.*\\|\\s*sh"),
        Pattern.compile("^php\\s+.*\\|\\s*sh"),
        Pattern.compile("^node\\s+.*\\|\\s*sh")
    );
    
    @Override
    public String getName() {
        return "Bash";  // 新名称
    }
    
    @Override
    public String getDescription() {
        return "执行 shell 命令并返回输出结果。\n" +
               "注意：命令会在工作目录下执行。\n" +
               "Windows 系统使用 'cmd /c'，Unix 系统使用 'sh -c'。";
    }
    
    @Override
    public PermissionLevel getDefaultPermission() {
        return PermissionLevel.ASK;  // 命令执行，需要用户确认
    }
    
    /**
     * 向后兼容：旧工具名
     */
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
            
            // 安全检查：黑名单检查（直接拒绝危险命令）
            for (Pattern dangerous : DANGEROUS_PATTERNS) {
                if (dangerous.matcher(command).find()) {
                    log.warn("[SECURITY] 命令被拒绝（危险模式匹配）: {}", command);
                    return ToolResult.failure("命令被安全策略拒绝：检测到危险命令模式");
                }
            }
            
            // 安全检查：白名单检查
            if (!isCommandAllowed(command)) {
                log.warn("[SECURITY] 命令被拒绝（不在白名单中）: {}", command);
                return ToolResult.failure("命令不在允许列表中。如需执行此命令，请联系管理员添加白名单。\n当前白名单包含：git, mvn, gradle, npm, pip, ls, dir, cat, grep 等常用命令。");
            }
            
            Integer timeout = (Integer) params.get("timeout");
            int timeoutSeconds = timeout != null ? timeout : DEFAULT_TIMEOUT_SECONDS;
            
            String cwd = (String) params.get("cwd");
            File workingDir = null;
            
            // 设置工作目录
            if (cwd != null && !cwd.isEmpty()) {
                workingDir = new File(cwd);
                log.debug("BashTool: cwd={}", workingDir.getAbsolutePath());
            }
            
            ProcessBuilder pb = new ProcessBuilder();
            
            // Windows vs Unix 命令处理
            if (System.getProperty("os.name").toLowerCase().contains("win")) {
                pb.command("cmd", "/c", command);
            } else {
                pb.command("sh", "-c", command);
            }
            
            // 设置工作目录（如果指定）
            if (workingDir != null) {
                pb.directory(workingDir);
            }
            
            pb.redirectErrorStream(true);

            log.debug("Executing command: {}", command);
            
            Process process = pb.start();
            
            StringBuilder output = new StringBuilder();
            try (BufferedReader reader = new BufferedReader(
                    new InputStreamReader(process.getInputStream()))) {
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
    
    /**
     * 检查命令是否在白名单中
     * 
     * @param command 待检查的命令
     * @return 如果命令被允许返回 true，否则返回 false
     */
    private boolean isCommandAllowed(String command) {
        // 1. 精确匹配
        if (ALLOWED_COMMANDS.contains(command.trim())) {
            return true;
        }
        
        // 2. 提取命令的第一部分（命令名）
        String commandName = command.trim().split("\\s+")[0];
        if (ALLOWED_COMMANDS.contains(commandName)) {
            return true;
        }
        
        // 3. 模式匹配
        for (Pattern pattern : ALLOWED_PATTERNS) {
            if (pattern.matcher(command).matches()) {
                return true;
            }
        }
        
        return false;
    }
}
