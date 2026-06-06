package com.codemind.impl.util;

/**
 * 项目常量定义
 *
 * 集中管理所有魔法数字和配置常量，便于维护和修改。
 *
 * @see <a href="AI_CODING_STANDARDS.md">编码规范</a>
 */
public final class ProjectConstants {

    // ==================== 输出格式化常量 ====================

    /** 输出内容最大显示长度 */
    public static final int MAX_DISPLAY_LENGTH = 100;

    /** 输出内容最大截断长度 */
    public static final int MAX_TRUNCATE_LENGTH = 34;

    // ==================== Token 相关常量 ====================

    /** 默认最大上下文 Token 数 */
    public static final int DEFAULT_MAX_CONTEXT_TOKENS = 8192;

    /** JTokkit 默认最大上下文 Token 数 */
    public static final int JTOKKIT_MAX_CONTEXT_TOKENS = 64000;

    /** DeepSeek 最大上下文 Token 数 */
    public static final int DEEPSEEK_MAX_CONTEXT_TOKENS = 16000;

    /** GPT-4 最大上下文 Token 数 */
    public static final int GPT4_MAX_CONTEXT_TOKENS = 200000;

    /** OpenAI 客户端默认最大 Token 数 */
    public static final int OPENAI_DEFAULT_MAX_TOKENS = 4096;

    /** 默认预留 Token 数 */
    public static final int DEFAULT_RESERVED_TOKENS = 1000;

    // ==================== 文件相关常量 ====================

    /** 文件最大大小（字节） */
    public static final int MAX_FILE_SIZE_BYTES = 1024 * 1024; // 1MB

    /** 编辑工具最大文件大小 */
    public static final int EDIT_MAX_FILE_SIZE = 1024 * 1024;

    // ==================== 安全相关常量 ====================

    /** 输入最大长度 */
    public static final int MAX_INPUT_LENGTH = 10000;

    /** 文件最大大小（MB） */
    public static final int MAX_FILE_SIZE_MB = 10;

    // ==================== 工具相关常量 ====================

    /** Grep 工具默认最大结果数 */
    public static final int GREP_MAX_RESULTS = 50;

    /** Bash 工具默认超时时间（秒） */
    public static final int BASH_DEFAULT_TIMEOUT_SECONDS = 30;

    // ==================== Agent 循环常量 ====================

    /** 默认最大迭代次数 */
    public static final int DEFAULT_MAX_ITERATIONS = 50;

    /** 默认超时时间（秒） */
    public static final int DEFAULT_TIMEOUT_SECONDS = 300;

    // ==================== 其他常量 ====================

    /** 私有构造函数，防止实例化 */
    private ProjectConstants() {
        throw new UnsupportedOperationException("Utility class cannot be instantiated");
    }
}
