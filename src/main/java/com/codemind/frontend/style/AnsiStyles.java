package com.codemind.frontend.style;

/**
 * ANSI 转义序列样式常量
 * 
 * 用于 CLI 输出格式化，提供颜色、背景、粗体等效果
 * 
 * 环境变量支持（参考主流 CLI 工具标准）：
 * - NO_COLOR: 设置后禁用所有颜色（https://no-color.org）
 * - CLICOLOR: 0 禁用颜色，1 启用颜色
 * - CLICOLOR_FORCE: 1 强制启用颜色（优先级最高）
 * 
 * 优先级：CLICOLOR_FORCE > NO_COLOR > CLICOLOR > 默认（自动检测）
 */
public class AnsiStyles {
    
    // ========== 环境变量检测 ==========
    
    private static final boolean COLOR_ENABLED;
    
    static {
        // 检测环境变量（优先级从高到低）
        String forceColor = System.getenv("CLICOLOR_FORCE");
        String noColor = System.getenv("NO_COLOR");
        String cliColor = System.getenv("CLICOLOR");
        
        if ("1".equals(forceColor) || "true".equalsIgnoreCase(forceColor)) {
            // CLICOLOR_FORCE=1 强制启用颜色
            COLOR_ENABLED = true;
        } else if (noColor != null && !noColor.isEmpty()) {
            // NO_COLOR 已设置，禁用颜色
            COLOR_ENABLED = false;
        } else if ("0".equals(cliColor) || "false".equalsIgnoreCase(cliColor)) {
            // CLICOLOR=0 禁用颜色
            COLOR_ENABLED = false;
        } else {
            // 默认：自动检测终端是否支持颜色
            COLOR_ENABLED = isTerminalSupportsColor();
        }
    }
    
    /**
     * 检测终端是否支持颜色
     */
    private static boolean isTerminalSupportsColor() {
        // 检查是否在终端环境中
        String term = System.getenv("TERM");
        if (term == null) {
            // Windows: 检查 ConEmuANSI 或 ANSICON
            String conEmuAnsi = System.getenv("ConEmuANSI");
            String ansicon = System.getenv("ANSICON");
            return "ON".equalsIgnoreCase(conEmuAnsi) || ansicon != null;
        }
        // Unix: 检查 TERM 变量
        return !term.equals("dumb") && !term.equals("unknown");
    }
    
    /**
     * 应用颜色（如果启用）
     */
    private static String color(String ansiCode) {
        return COLOR_ENABLED ? ansiCode : "";
    }
    
    // ========== 重置 ==========
    
    public static final String RESET = color("\u001B[0m");
    
    // ========== 样式 ==========
    
    public static final String BOLD = color("\u001B[1m");
    public static final String DIM = color("\u001B[2m");
    public static final String ITALIC = color("\u001B[3m");
    public static final String UNDERLINE = color("\u001B[4m");
    
    // ========== 前景色 ==========
    
    public static final String BLACK = color("\u001B[30m");
    public static final String RED = color("\u001B[31m");
    public static final String GREEN = color("\u001B[32m");
    public static final String YELLOW = color("\u001B[33m");
    public static final String BLUE = color("\u001B[34m");
    public static final String MAGENTA = color("\u001B[35m");
    public static final String CYAN = color("\u001B[36m");
    public static final String WHITE = color("\u001B[37m");
    
    // ========== 背景色 ==========
    
    public static final String BG_BLACK = color("\u001B[40m");
    public static final String BG_RED = color("\u001B[41m");
    public static final String BG_GREEN = color("\u001B[42m");
    public static final String BG_YELLOW = color("\u001B[43m");
    public static final String BG_BLUE = color("\u001B[44m");
    public static final String BG_MAGENTA = color("\u001B[45m");
    public static final String BG_CYAN = color("\u001B[46m");
    public static final String BG_WHITE = color("\u001B[47m");
    
    // ========== 控制序列 ==========
    
    /**
     * 清除当前行
     */
    public static final String CLEAR_LINE = COLOR_ENABLED ? "\u001B[2K\r" : "\r";
    
    /**
     * 移动光标到行首
     */
    public static final String CARRIAGE_RETURN = "\r";
    
}