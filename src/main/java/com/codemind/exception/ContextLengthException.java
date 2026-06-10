package com.codemind.exception;

/**
 * 表示 LLM API 上下文长度超限异常。
 * <p>
 * 当 API 返回 prompt_too_long / context_length_exceeded / maximum context length
 * 等错误时抛出此异常，供调用方据此触发上下文压缩或裁剪策略。
 * </p>
 */
public class ContextLengthException extends RuntimeException {

    public ContextLengthException(String message, Throwable cause) {
        super(message, cause);
    }
}
