package com.codemind.common.exception;

/**
 * LLM 调用异常。LLM 客户端调用失败时抛出。
 */
public class LLMException extends AgentException {

    public LLMException(String message) {
        super(message);
    }

    public LLMException(String message, Throwable cause) {
        super(message, cause);
    }
}
