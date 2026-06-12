package com.codemind.common.exception;

/**
 * 会话异常。会话管理相关操作失败时抛出。
 */
public class SessionException extends AgentException {

    public SessionException(String message) {
        super(message);
    }

    public SessionException(String message, Throwable cause) {
        super(message, cause);
    }
}
