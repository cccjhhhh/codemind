package com.codemind.api.skill;

/**
 * 技能执行结果
 */
public class SkillResult {
    
    private final boolean success;
    private final String output;
    private final String error;
    
    public SkillResult(boolean success, String output, String error) {
        this.success = success;
        this.output = output;
        this.error = error;
    }
    
    public static SkillResult success(String output) {
        return new SkillResult(true, output, null);
    }
    
    public static SkillResult failure(String error) {
        return new SkillResult(false, null, error);
    }
    
    // Getters
    public boolean isSuccess() { return success; }
    public String getOutput() { return output; }
    public String getError() { return error; }
}