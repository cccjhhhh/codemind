package com.codemind.core;

/**
 * Agent 执行结果
 */
public class AgentResult {
    
    private final boolean success;
    private final String output;
    private final String error;
    private final int iterations;
    
    public AgentResult(boolean success, String output, String error, int iterations) {
        this.success = success;
        this.output = output;
        this.error = error;
        this.iterations = iterations;
    }
    
    public static AgentResult success(String output) {
        return new AgentResult(true, output, null, 0);
    }
    
    public static AgentResult failure(String error) {
        return new AgentResult(false, null, error, 0);
    }
    
    // Getters
    public boolean isSuccess() { return success; }
    public String getOutput() { return output; }
    public String getError() { return error; }
    public int getIterations() { return iterations; }
}