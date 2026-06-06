package com.codemind.api.tool;

import com.codemind.api.safety.PermissionLevel;

/**
 * 工具执行结果
 */
public class ToolResult {

    private final boolean success;
    private final String output;
    private final String error;
    private final PermissionLevel requiredLevel;

    public ToolResult(boolean success, String output, String error) {
        this(success, output, error, null);
    }

    public ToolResult(boolean success, String output, String error, PermissionLevel requiredLevel) {
        this.success = success;
        this.output = output;
        this.error = error;
        this.requiredLevel = requiredLevel;
    }

    public static ToolResult success(String output) {
        return new ToolResult(true, output, null, null);
    }

    public static ToolResult failure(String error) {
        return new ToolResult(false, null, error, null);
    }

    /**
     * 创建需要确认的结果（工具执行被权限 gate 拦截）
     */
    public static ToolResult needsConfirmation(PermissionLevel level) {
        return new ToolResult(false, null, null, level);
    }

    // Getters
    public boolean isSuccess() { return success; }
    public String getOutput() { return output; }
    public String getError() { return error; }
    public PermissionLevel getRequiredLevel() { return requiredLevel; }
    public boolean needsConfirmation() { return requiredLevel == PermissionLevel.ASK; }
}
