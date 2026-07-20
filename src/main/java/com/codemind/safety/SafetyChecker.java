package com.codemind.safety;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.file.Path;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;
import java.util.regex.Pattern;

/**
 * 安全检查器
 * 
 * 对用户输入和 Agent 输出进行安全检查。
 * 支持 DENY/ASK/ALLOW 三级权限控制。
 */
public class SafetyChecker {

    private static final Logger log = LoggerFactory.getLogger(SafetyChecker.class);

    // ==================== 权限级别 ====================
    
    public enum PermissionLevel {
        DENY,   // 直接拒绝
        ASK,    // 需要用户确认
        ALLOW   // 允许执行
    }
    
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
    
    // ==================== 危险命令映射 ====================
    
    private static final java.util.Map<String, PermissionLevel> DANGEROUS_COMMANDS = new ConcurrentHashMap<>();
    
    static {
        // 高危命令 - 直接拒绝
        DANGEROUS_COMMANDS.put("rm -rf /", PermissionLevel.DENY);
        DANGEROUS_COMMANDS.put("rm -rf /*", PermissionLevel.DENY);
        DANGEROUS_COMMANDS.put("mkfs", PermissionLevel.DENY);
        DANGEROUS_COMMANDS.put("dd if=/dev/zero", PermissionLevel.DENY);
        DANGEROUS_COMMANDS.put("format", PermissionLevel.DENY);
        
        // 中危命令 - 需要确认
        DANGEROUS_COMMANDS.put("rm -rf", PermissionLevel.ASK);
        DANGEROUS_COMMANDS.put("rm -r", PermissionLevel.ASK);
        DANGEROUS_COMMANDS.put("DROP TABLE", PermissionLevel.ASK);
        DANGEROUS_COMMANDS.put("DELETE FROM", PermissionLevel.ASK);
        DANGEROUS_COMMANDS.put("UPDATE", PermissionLevel.ASK);
        DANGEROUS_COMMANDS.put("sudo", PermissionLevel.ASK);
        DANGEROUS_COMMANDS.put("chmod 777", PermissionLevel.ASK);
        DANGEROUS_COMMANDS.put("chown", PermissionLevel.ASK);
        
        // 低危命令 - 允许
        DANGEROUS_COMMANDS.put("ls", PermissionLevel.ALLOW);
        DANGEROUS_COMMANDS.put("cat", PermissionLevel.ALLOW);
        DANGEROUS_COMMANDS.put("grep", PermissionLevel.ALLOW);
        DANGEROUS_COMMANDS.put("find", PermissionLevel.ALLOW);
    }
    
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
    
    // ==================== 指标统计 ====================
    
    private final AtomicLong totalChecks = new AtomicLong(0);
    private final AtomicLong deniedChecks = new AtomicLong(0);
    private final AtomicLong askChecks = new AtomicLong(0);
    private final AtomicLong allowedChecks = new AtomicLong(0);
    private final AtomicLong sanitizedOutputs = new AtomicLong(0);
    
    // ==================== 输入安全检查 ====================
    
    /**
     * 检查用户输入是否安全
     */
    public boolean isInputSafe(String input) {
        if (input == null || input.isEmpty()) {
            return true;
        }
        
        totalChecks.incrementAndGet();
        
        // 长度检查
        if (input.length() > MAX_INPUT_LENGTH) {
            deniedChecks.incrementAndGet();
            log.warn("输入安全检查失败: 长度超过限制 ({} > {})", input.length(), MAX_INPUT_LENGTH);
            return false;
        }
        
        // 危险模式检测
        for (Pattern pattern : DANGEROUS_PATTERNS) {
            if (pattern.matcher(input).find()) {
                deniedChecks.incrementAndGet();
                log.warn("输入安全检查失败: 检测到危险模式");
                return false;
            }
        }
        
        allowedChecks.incrementAndGet();
        return true;
    }
    
    /**
     * 检查命令权限级别
     */
    public PermissionLevel checkCommandPermission(String command) {
        if (command == null || command.isEmpty()) {
            return PermissionLevel.ALLOW;
        }
        
        totalChecks.incrementAndGet();
        
        String lowerCommand = command.toLowerCase().trim();
        
        // 检查危险命令映射
        for (java.util.Map.Entry<String, PermissionLevel> entry : DANGEROUS_COMMANDS.entrySet()) {
            if (lowerCommand.contains(entry.getKey().toLowerCase())) {
                PermissionLevel level = entry.getValue();
                switch (level) {
                    case DENY:
                        deniedChecks.incrementAndGet();
                        log.warn("命令权限检查: DENY (匹配: {})", entry.getKey());
                        break;
                    case ASK:
                        askChecks.incrementAndGet();
                        log.info("命令权限检查: ASK (匹配: {})", entry.getKey());
                        break;
                    default:
                        allowedChecks.incrementAndGet();
                }
                return level;
            }
        }
        
        // 默认允许
        allowedChecks.incrementAndGet();
        return PermissionLevel.ALLOW;
    }
    
    /**
     * 检测 Prompt 注入
     */
    public boolean detectPromptInjection(String input) {
        if (input == null || input.isEmpty()) {
            return false;
        }
        
        totalChecks.incrementAndGet();
        
        // 检测常见的 Prompt 注入模式
        String lowerInput = input.toLowerCase();
        
        boolean detected = lowerInput.contains("ignore previous instructions") ||
               lowerInput.contains("disregard all previous instructions") ||
               lowerInput.contains("ignore system prompt") ||
               lowerInput.contains("new instructions:") ||
               lowerInput.contains("override");
        
        if (detected) {
            deniedChecks.incrementAndGet();
            log.warn("检测到 Prompt 注入尝试");
        }
        
        return detected;
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
        
        if (!sanitized.equals(output)) {
            sanitizedOutputs.incrementAndGet();
            log.debug("输出敏感信息过滤: 检测到并替换了敏感信息");
        }
        
        return sanitized;
    }
    
    // ==================== 指标查询 ====================
    
    /**
     * 获取安全检查统计信息
     */
    public String getMetrics() {
        return String.format(
            "TotalChecks=%d, Denied=%d, Ask=%d, Allowed=%d, SanitizedOutputs=%d",
            totalChecks.get(), deniedChecks.get(), askChecks.get(),
            allowedChecks.get(), sanitizedOutputs.get()
        );
    }
    
    /**
     * 重置指标统计
     */
    public void resetMetrics() {
        totalChecks.set(0);
        deniedChecks.set(0);
        askChecks.set(0);
        allowedChecks.set(0);
        sanitizedOutputs.set(0);
        log.info("安全检查指标已重置");
    }
}
