package com.codemind.api.tool;

/**
 * 工具执行结果
 */
public class ToolResult {

    private boolean success;
    private String output;
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

    public boolean isSuccess() { return success; }
    public String getOutput() { return output; }
    public String getError() { return error; }

    /** 替换输出内容（用于 TruncationHook 进行大结果落盘替换） */
    public void setOutput(String newOutput) {
        this.output = newOutput;
    }
}
