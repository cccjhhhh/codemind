package com.codemind.common.exception;

/**
 * 工具权限拒绝异常。
 */
public class ToolPermissionException extends ToolExecutionException {

    public ToolPermissionException(String message) {
        super(message);
    }

    public ToolPermissionException(String message, Throwable cause) {
        super(message, cause);
    }
}
