package com.codemind.common.exception;

/**
 * LLM 调用超时异常。
 */
public class LLMTimeoutException extends LLMException {

    public LLMTimeoutException(String message) {
        super(message);
    }

    public LLMTimeoutException(String message, Throwable cause) {
        super(message, cause);
    }
}
