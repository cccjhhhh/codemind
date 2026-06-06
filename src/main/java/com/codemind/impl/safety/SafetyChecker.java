package com.codemind.impl.safety;

import java.nio.file.Path;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * 安全检查器
 * 
 * 对用户输入和 Agent 输出进行安全检查。
 * 学习要点：输入验证、Prompt 注入检测、输出过滤、路径安全
 */
public class SafetyChecker {
    
    // ==================== 危险模式检测 ====================
    
    // 命令/路径注入模式
    private static final Pattern[] DANGEROUS_PATTERNS = {
        // 命令注入
        Pattern.compile(";\\s*(rm|del|format)", Pattern.CASE_INSENSITIVE),
        Pattern.compile("&&\\s*(rm|del|format)", Pattern.CASE_INSENSITIVE),
        Pattern.compile("\\|\\s*(rm|del|format)", Pattern.CASE_INSENSITIVE),
        
        // 路径遍历
        Pattern.compile("\\.\\.\\/"),
        Pattern.compile("\\.\\.\\\\"),
        
        // Prompt 注入常见模式
        Pattern.compile("ignore.*previous.*instructions", Pattern.CASE_INSENSITIVE),
        Pattern.compile("disregard.*instructions", Pattern.CASE_INSENSITIVE),
        Pattern.compile("you.*are.*now.*", Pattern.CASE_INSENSITIVE),
    };
    
    private static final int MAX_INPUT_LENGTH = 10000;
    private static final int MAX_FILE_SIZE_MB = 10;
    
    // 敏感信息模式定义
    private static final Pattern[] SENSITIVE_PATTERNS = {
        // OpenAI API Key: sk-xxx 或 sk-proj-xxx
        Pattern.compile("sk-[a-zA-Z0-9]{20,}"),
        Pattern.compile("sk-proj-[a-zA-Z0-9]{20,}"),
        
        // AWS Access Key: AKIA 开头，后跟 16 位大写字母数字
        Pattern.compile("AKIA[A-Z0-9]{16}"),
        
        // JWT Token: 三段 base64 编码，以 eyJ 开头
        Pattern.compile("eyJ[a-zA-Z0-9_-]*\\.eyJ[a-zA-Z0-9_-]*\\.[a-zA-Z0-9_-]*"),
        
        // Bearer Token: Bearer 后跟任意 token
        Pattern.compile("Bearer\\s+[a-zA-Z0-9._-]+", Pattern.CASE_INSENSITIVE),
        
        // 密码字段: password=xxx 或 "password": "xxx"
        Pattern.compile("(password|passwd|pwd)[\"']?\\s*[=:]\\s*[\"']?[^\\s\"']+[\"']?", Pattern.CASE_INSENSITIVE),
        
        // API Key 字段: api_key=xxx 或 apiKey: xxx
        Pattern.compile("(api_key|apikey|api-key)[\"']?\\s*[=:]\\s*[\"']?[^\\s\"']+[\"']?", Pattern.CASE_INSENSITIVE),
        
        // 私钥: -----BEGIN PRIVATE KEY-----
        Pattern.compile("-----BEGIN[A-Z\\s]*PRIVATE KEY-----[\\s\\S]*?-----END[A-Z\\s]*PRIVATE KEY-----"),
        
        // 信用卡号: 16位数字，可带分隔符
        Pattern.compile("\\d{4}[-\\s]?\\d{4}[-\\s]?\\d{4}[-\\s]?\\d{4}"),
    };
    
    private static final String REDACTED = "[REDACTED]";
    
    // ==================== 路径安全检查 ====================
    
    // 路径遍历模式
    private static final Pattern PATH_TRAVERSAL = Pattern.compile("\\.\\.(/|\\\\)");
    
    // Windows 危险路径
    private static final Set<String> WINDOWS_CRITICAL_PATHS = Set.of(
        "c:\\windows",
        "c:\\program files",
        "c:\\program files (x86)",
        "c:\\system32",
        "c:\\boot",
        "c:\\recovery"
    );
    
    // Unix 危险路径
    private static final Set<String> UNIX_CRITICAL_PATHS = Set.of(
        "/etc",
        "/usr/bin",
        "/usr/sbin",
        "/bin",
        "/sbin",
        "/boot",
        "/sys",
        "/proc",
        "/dev",
        "/root"
    );
    
    // 危险命令模式
    private static final Set<Pattern> CMD_DANGEROUS_PATTERNS = Set.of(
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
        Pattern.compile("curl\\s+.*\\|\\s*sh"),
        Pattern.compile("wget\\s+.*\\|\\s*sh"),
        Pattern.compile("python\\s+.*\\|\\s*sh"),
        Pattern.compile("perl\\s+.*\\|\\s*sh"),
        Pattern.compile("ruby\\s+.*\\|\\s*sh"),
        Pattern.compile("php\\s+.*\\|\\s*sh"),
        Pattern.compile("node\\s+.*\\|\\s*sh"),
        Pattern.compile("^sudo\\s+"),
        Pattern.compile("chmod\\s+777"),
        Pattern.compile("chmod\\s+-R\\s+777")
    );
    
    // ==================== 输入安全检查 ====================
    
    /**
     * 检查用户输入是否安全
     */
    public boolean isInputSafe(String input) {
        if (input == null || input.isEmpty()) {
            return true;
        }
        
        // 长度检查
        if (input.length() > MAX_INPUT_LENGTH) {
            return false;
        }
        
        // 危险模式检测
        for (Pattern pattern : DANGEROUS_PATTERNS) {
            if (pattern.matcher(input).find()) {
                return false;
            }
        }
        
        return true;
    }
    
    /**
     * 检测 Prompt 注入
     */
    public boolean detectPromptInjection(String input) {
        if (input == null || input.isEmpty()) {
            return false;
        }
        
        // 检测常见的 Prompt 注入模式
        String lowerInput = input.toLowerCase();
        
        return lowerInput.contains("ignore previous instructions") ||
               lowerInput.contains("disregard all previous instructions") ||
               lowerInput.contains("ignore system prompt") ||
               lowerInput.contains("new instructions:") ||
               lowerInput.contains("override");
    }
    
    /**
     * 检查文件大小是否安全
     */
    public boolean isFileSizeSafe(long sizeBytes) {
        return sizeBytes <= MAX_FILE_SIZE_MB * 1024 * 1024;
    }
    
    // ==================== 敏感信息过滤 ====================
    
    /**
     * 过滤输出中的敏感信息
     * 
     * 将敏感信息替换为 [REDACTED] 标记，保持输出可读性。
     */
    public String sanitizeOutput(String output) {
        if (output == null) {
            return null;
        }
        
        String sanitized = output;
        
        // 逐个检测并替换敏感信息
        for (Pattern pattern : SENSITIVE_PATTERNS) {
            sanitized = pattern.matcher(sanitized).replaceAll(REDACTED);
        }
        
        return sanitized;
    }
    
    /**
     * 检查输出是否包含敏感信息
     */
    public boolean containsSensitiveInfo(String output) {
        if (output == null || output.isEmpty()) {
            return false;
        }
        
        for (Pattern pattern : SENSITIVE_PATTERNS) {
            if (pattern.matcher(output).find()) {
                return true;
            }
        }
        
        return false;
    }
    
    // ==================== 路径安全检查 ====================
    
    /**
     * 检查是否为路径遍历攻击
     */
    public boolean isPathTraversal(String input) {
        if (input == null) {
            return false;
        }
        return PATH_TRAVERSAL.matcher(input).find();
    }
    
    /**
     * 检查是否为危险命令
     */
    public boolean isDangerousCommand(String input) {
        if (input == null) {
            return false;
        }
        
        for (Pattern pattern : CMD_DANGEROUS_PATTERNS) {
            if (pattern.matcher(input).find()) {
                return true;
            }
        }
        return false;
    }
    
    /**
     * 检查路径是否安全（不在系统关键目录）
     */
    public boolean isPathSafe(String pathStr) {
        if (pathStr == null) {
            return false;
        }
        
        String lowerPath = pathStr.toLowerCase();
        
        if (System.getProperty("os.name").toLowerCase().contains("win")) {
            return !WINDOWS_CRITICAL_PATHS.stream()
                .anyMatch(lowerPath::startsWith);
        } else {
            return !UNIX_CRITICAL_PATHS.stream()
                .anyMatch(lowerPath::startsWith);
        }
    }
    
    /**
     * 检查路径是否安全
     */
    public boolean isPathSafe(Path path) {
        return isPathSafe(path.toString());
    }
    
    /**
     * 规范化路径（解析 .. 等相对路径）
     */
    public Path sanitizePath(String input) {
        if (input == null) {
            return null;
        }
        
        try {
            Path path = Path.of(input);
            // 解析相对路径（移除 .. 等）
            return path.toAbsolutePath().normalize();
        } catch (Exception e) {
            return null;
        }
    }
    
    /**
     * 检查文件路径是否在允许的目录下
     */
    public boolean isWithinAllowedDir(Path filePath, Path allowedDir) {
        try {
            Path normalizedFile = filePath.toAbsolutePath().normalize();
            Path normalizedDir = allowedDir.toAbsolutePath().normalize();
            return normalizedFile.startsWith(normalizedDir);
        } catch (Exception e) {
            return false;
        }
    }
    
    /**
     * 获取危险命令的描述
     */
    public String getDangerousCommandReason(String input) {
        if (input == null) {
            return null;
        }
        
        for (Pattern pattern : CMD_DANGEROUS_PATTERNS) {
            if (pattern.matcher(input).find()) {
                return "危险命令模式: " + pattern.pattern();
            }
        }
        return null;
    }
}
