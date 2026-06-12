package com.codemind.common.exception;

/**
 * 上下文长度超出异常。
 * 当对话历史 + 系统提示的总 token 数超过模型上下文窗口时抛出。
 */
public class ContextOverflowException extends AgentException {

    public ContextOverflowException(String message) {
        super(message);
    }

    public ContextOverflowException(String message, Throwable cause) {
        super(message, cause);
    }
}
