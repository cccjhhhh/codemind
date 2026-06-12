package com.codemind.common.exception;

/**
 * CodeMind 异常基类。
 *
 * 所有业务异常继承此类，调用方按需 catch AgentException 即可统一处理。
 */
public class AgentException extends RuntimeException {

    public AgentException(String message) {
        super(message);
    }

    public AgentException(String message, Throwable cause) {
        super(message, cause);
    }
}
