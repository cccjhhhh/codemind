package com.codemind.common.exception;

/**
 * LLM 限流异常（对应 HTTP 429）。
 */
public class LLMRateLimitException extends LLMException {

    public LLMRateLimitException(String message) {
        super(message);
    }

    public LLMRateLimitException(String message, Throwable cause) {
        super(message, cause);
    }
}
