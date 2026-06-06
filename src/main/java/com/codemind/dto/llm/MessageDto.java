package com.codemind.dto.llm;

import com.codemind.api.skill.SkillResult;

import java.util.List;

/**
 * 消息
 */
public class MessageDto {
    
    public enum Role {
        SYSTEM, USER, ASSISTANT, TOOL
    }
    
    private final Role role;
    private final String content;
    private final String toolCallId;
    private final List<ToolCallDto> toolCalls;
    
    // 基础构造器
    public MessageDto(Role role, String content) {
        this.role = role;
        this.content = content;
        this.toolCallId = null;
        this.toolCalls = null;
    }
    
    // TOOL 角色构造器
    public MessageDto(Role role, String content, String toolCallId) {
        this.role = role;
        this.content = content;
        this.toolCallId = toolCallId;
        this.toolCalls = null;
    }
    
    // ASSISTANT 角色带 tool_calls 构造器
    public MessageDto(Role role, String content, List<ToolCallDto> toolCalls) {
        this.role = role;
        this.content = content;
        this.toolCallId = null;
        this.toolCalls = toolCalls;
    }
    
    // ========== 静态工厂方法 ==========
    
    public static MessageDto system(String content) {
        return new MessageDto(Role.SYSTEM, content);
    }
    
    public static MessageDto user(String content) {
        return new MessageDto(Role.USER, content);
    }
    
    public static MessageDto assistant(String content) {
        return new MessageDto(Role.ASSISTANT, content);
    }
    
    public static MessageDto assistantWithTools(String content, List<ToolCallDto> toolCalls) {
        return new MessageDto(Role.ASSISTANT, content, toolCalls);
    }
    
    public static MessageDto tool(String content, String toolCallId) {
        return new MessageDto(Role.TOOL, content, toolCallId);
    }
    
    /**
     * 创建 Skill 结果消息
     * 
     * 注意：Skill 结果不是真正的工具调用响应（没有 tool_call_id），
     * 因此使用 ASSISTANT 角色而不是 TOOL 角色。
     * TOOL 角色需要有效的 tool_call_id，OpenAI API 会拒绝 null 值。
     * 
     * @param result Skill 执行结果
     * @return 消息
     */
    public static MessageDto skillResult(SkillResult result) {
        String content = result.isSuccess() 
            ? "Skill 执行结果:\n" + result.getOutput()
            : "Skill 执行失败: " + result.getError();
        return new MessageDto(Role.ASSISTANT, content);
    }
    
    // ========== Getter 方法 ==========
    
    public Role getRole() { return role; }
    public String getContent() { return content; }
    public String getToolCallId() { return toolCallId; }
    public List<ToolCallDto> getToolCalls() { return toolCalls; }
    public boolean hasToolCalls() { return toolCalls != null && !toolCalls.isEmpty(); }
}
