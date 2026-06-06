package com.codemind.dto.tool;

import com.codemind.api.safety.PermissionLevel;

/**
 * 工具执行结果数据传输对象
 */
public class ToolResultDto {

    private final boolean success;
    private final String output;
    private final String error;
    private final boolean needsConfirmation;
    private final PermissionLevel requiredLevel;

    public ToolResultDto(boolean success, String output, String error) {
        this(success, output, error, false, null);
    }

    public ToolResultDto(boolean success, String output, String error,
                         boolean needsConfirmation, PermissionLevel requiredLevel) {
        this.success = success;
        this.output = output;
        this.error = error;
        this.needsConfirmation = needsConfirmation;
        this.requiredLevel = requiredLevel;
    }

    public static ToolResultDto success(String output) {
        return new ToolResultDto(true, output, null, false, null);
    }

    public static ToolResultDto failure(String error) {
        return new ToolResultDto(false, null, error, false, null);
    }

    public static ToolResultDto needsConfirmation(PermissionLevel level) {
        return new ToolResultDto(false, null, null, true, level);
    }

    public boolean isSuccess() { return success; }
    public String getOutput() { return output; }
    public String getError() { return error; }
    public boolean needsConfirmation() { return needsConfirmation; }
    public PermissionLevel getRequiredLevel() { return requiredLevel; }
}
