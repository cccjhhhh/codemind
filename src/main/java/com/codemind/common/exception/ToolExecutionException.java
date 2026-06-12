package com.codemind.common.exception;

/**
 * 工具执行异常。工具调用失败时抛出。
 */
public class ToolExecutionException extends AgentException {

    public ToolExecutionException(String message) {
        super(message);
    }

    public ToolExecutionException(String message, Throwable cause) {
        super(message, cause);
    }
}
