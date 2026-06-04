package com.codemind.impl.safety;

import java.util.regex.Pattern;

/**
 * 安全检查器
 * 
 * 对用户输入和 Agent 输出进行安全检查。
 * 学习要点：输入验证、Prompt 注入检测、输出过滤
 */
public class SafetyChecker {
    
    // 危险模式检测
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
    
    /**
     * 敏感信息模式定义
     * 
     * 学习要点：
     * 1. 正则表达式匹配敏感信息格式
     * 2. 每种敏感信息有其特定的格式特征
     * 3. 替换为 [REDACTED] 保持输出可读性
     */
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
    
    /**
     * 过滤输出中的敏感信息
     * 
     * 将敏感信息替换为 [REDACTED] 标记，保持输出可读性。
     * 
     * 学习要点：
     * 1. 敏感信息包括：API Keys、密码、Token、私钥、信用卡号
     * 2. 过滤后的输出仍能显示原有信息的存在位置
     * 3. 防止敏感信息泄露到日志或对话历史中
     * 
     * @param output 原始输出字符串
     * @return 过滤后的安全字符串
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
     * 
     * 用于检测和警告，但不修改输出。
     * 
     * @param output 待检查的输出
     * @return 是否包含敏感信息
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
}