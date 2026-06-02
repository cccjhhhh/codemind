package com.codemind.impl.cli;

/**
 * ANSI 转义序列样式常量
 * 
 * 用于 CLI 输出格式化，提供颜色、背景、粗体等效果
 */
public class AnsiStyles {
    
    // 重置
    public static final String RESET = "\u001B[0m";
    
    // 粗体
    public static final String BOLD = "\u001B[1m";
    public static final String DIM = "\u001B[2m";
    public static final String ITALIC = "\u001B[3m";
    public static final String UNDERLINE = "\u001B[4m";
    
    // 前景色
    public static final String BLACK = "\u001B[30m";
    public static final String RED = "\u001B[31m";
    public static final String GREEN = "\u001B[32m";
    public static final String YELLOW = "\u001B[33m";
    public static final String BLUE = "\u001B[34m";
    public static final String MAGENTA = "\u001B[35m";
    public static final String CYAN = "\u001B[36m";
    public static final String WHITE = "\u001B[37m";
    
    // 背景色
    public static final String BG_BLACK = "\u001B[40m";
    public static final String BG_RED = "\u001B[41m";
    public static final String BG_GREEN = "\u001B[42m";
    public static final String BG_YELLOW = "\u001B[43m";
    public static final String BG_BLUE = "\u001B[44m";
    public static final String BG_MAGENTA = "\u001B[45m";
    public static final String BG_CYAN = "\u001B[46m";
    public static final String BG_WHITE = "\u001B[47m";
    
    /**
     * 截断字符串到指定长度
     */
    public static String truncate(String str, int maxLength) {
        if (str == null) return "";
        if (str.length() <= maxLength) return str;
        return str.substring(0, maxLength - 3) + "...";
    }
    
    /**
     * 清除当前行
     */
    public static final String CLEAR_LINE = "\u001B[2K\r";
    
    /**
     * 移动光标到行首
     */
    public static final String CARRIAGE_RETURN = "\r";
}