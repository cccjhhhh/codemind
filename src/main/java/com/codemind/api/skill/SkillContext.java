package com.codemind.api.skill;

import com.codemind.api.session.SessionContext;
import com.codemind.api.tool.ToolRegistry;
import com.codemind.api.tool.ToolResult;

import java.util.Map;

/**
 * 技能执行上下文
 * 
 * 包含技能执行所需的所有信息，包括调用其他 Tool 的能力
 * 
 * 学习要点：
 * - Skill 如何通过 context 调用其他 Tool
 * - 依赖注入：ToolRegistry 通过构造器注入
 */
public class SkillContext {
    
    private final SessionContext sessionContext;
    private final String skillName;
    private final String userInput;
    private final ToolRegistry toolRegistry;
    
    public SkillContext(SessionContext sessionContext, String skillName, String userInput) {
        this(sessionContext, skillName, userInput, null);
    }
    
    public SkillContext(SessionContext sessionContext, String skillName, String userInput, ToolRegistry toolRegistry) {
        this.sessionContext = sessionContext;
        this.skillName = skillName;
        this.userInput = userInput;
        this.toolRegistry = toolRegistry;
    }
    
    /**
     * 调用其他 Tool
     * 
     * Skill 可以通过此方法调用注册在 ToolRegistry 中的其他 Tool，
     * 实现复杂任务的编排
     * 
     * @param toolName 工具名称
     * @param params 工具参数
     * @return 工具执行结果
     */
    public ToolResult callTool(String toolName, Map<String, Object> params) {
        if (toolRegistry == null) {
            return ToolResult.failure("ToolRegistry not available in SkillContext");
        }
        return toolRegistry.execute(toolName, params);
    }
    
    /**
     * 检查是否有 ToolRegistry
     */
    public boolean hasToolRegistry() {
        return toolRegistry != null;
    }
    
    // Getters
    public SessionContext getSessionContext() { return sessionContext; }
    public String getSkillName() { return skillName; }
    public String getUserInput() { return userInput; }
}