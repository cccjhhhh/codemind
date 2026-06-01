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
     * 过滤输出中的敏感信息
     */
    public String sanitizeOutput(String output) {
        if (output == null) {
            return null;
        }
        
        // TODO: 实现敏感信息过滤（如 API key、token 等）
        // 这是一个基础版本，实际应该更复杂
        
        return output;
    }
}