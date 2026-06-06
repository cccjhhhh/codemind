package com.codemind.dto.skill;

import com.codemind.api.session.SessionContext;
import com.codemind.api.tool.ToolRegistry;
import com.codemind.api.tool.ToolResult;

import java.util.Map;

/**
 * 技能执行上下文数据传输对象
 */
public class SkillContextDto {
    
    private final SessionContext sessionContext;
    private final String skillName;
    private final String userInput;
    private final ToolRegistry toolRegistry;
    
    public SkillContextDto(SessionContext sessionContext, String skillName, String userInput) {
        this(sessionContext, skillName, userInput, null);
    }
    
    public SkillContextDto(SessionContext sessionContext, String skillName, String userInput, ToolRegistry toolRegistry) {
        this.sessionContext = sessionContext;
        this.skillName = skillName;
        this.userInput = userInput;
        this.toolRegistry = toolRegistry;
    }
    
    public ToolResult callTool(String toolName, Map<String, Object> params) {
        if (toolRegistry == null) {
            return ToolResult.failure("ToolRegistry not available in SkillContext");
        }
        return toolRegistry.execute(toolName, params);
    }
    
    public boolean hasToolRegistry() {
        return toolRegistry != null;
    }
    
    public SessionContext getSessionContext() { return sessionContext; }
    public String getSkillName() { return skillName; }
    public String getUserInput() { return userInput; }
}
