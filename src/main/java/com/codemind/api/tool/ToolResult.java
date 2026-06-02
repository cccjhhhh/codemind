package com.codemind.api.tool;

import com.codemind.api.safety.Permission;

/**
 * 工具执行结果
 */
public class ToolResult {
    
    private final boolean success;
    private final String output;
    private final String error;
    private final boolean needsConfirmation;
    private final Permission requiredPermission;
    
    public ToolResult(boolean success, String output, String error) {
        this(success, output, error, false, null);
    }
    
    public ToolResult(boolean success, String output, String error, 
                      boolean needsConfirmation, Permission requiredPermission) {
        this.success = success;
        this.output = output;
        this.error = error;
        this.needsConfirmation = needsConfirmation;
        this.requiredPermission = requiredPermission;
    }
    
    public static ToolResult success(String output) {
        return new ToolResult(true, output, null, false, null);
    }
    
    public static ToolResult failure(String error) {
        return new ToolResult(false, null, error, false, null);
    }
    
    /**
     * 创建需要确认的结果（工具执行被权限 gate 拦截）
     */
    public static ToolResult needsConfirmation(Permission permission) {
        return new ToolResult(false, null, null, true, permission);
    }
    
    // Getters
    public boolean isSuccess() { return success; }
    public String getOutput() { return output; }
    public String getError() { return error; }
    public boolean needsConfirmation() { return needsConfirmation; }
    public Permission getRequiredPermission() { return requiredPermission; }
}