package com.codemind.dto.skill;

/**
 * 技能执行结果数据传输对象
 */
public class SkillResultDto {
    
    private final boolean success;
    private final String output;
    private final String error;
    
    public SkillResultDto(boolean success, String output, String error) {
        this.success = success;
        this.output = output;
        this.error = error;
    }
    
    public static SkillResultDto success(String output) {
        return new SkillResultDto(true, output, null);
    }
    
    public static SkillResultDto failure(String error) {
        return new SkillResultDto(false, null, error);
    }
    
    public boolean isSuccess() { return success; }
    public String getOutput() { return output; }
    public String getError() { return error; }
}
