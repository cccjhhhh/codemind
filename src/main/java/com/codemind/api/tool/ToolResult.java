package com.codemind.api.tool;

/**
 * 工具执行结果
 */
public class ToolResult {
    
    private final boolean success;
    private final String output;
    private final String error;
    
    public ToolResult(boolean success, String output, String error) {
        this.success = success;
        this.output = output;
        this.error = error;
    }
    
    public static ToolResult success(String output) {
        return new ToolResult(true, output, null);
    }
    
    public static ToolResult failure(String error) {
        return new ToolResult(false, null, error);
    }
    
    // Getters
    public boolean isSuccess() { return success; }
    public String getOutput() { return output; }
    public String getError() { return error; }
}