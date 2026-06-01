package com.codemind.api.skill;

import com.codemind.api.session.SessionContext;

/**
 * 技能执行上下文
 * 
 * 包含技能执行所需的所有信息
 */
public class SkillContext {
    
    private final SessionContext sessionContext;
    private final String skillName;
    private final String userInput;
    
    public SkillContext(SessionContext sessionContext, String skillName, String userInput) {
        this.sessionContext = sessionContext;
        this.skillName = skillName;
        this.userInput = userInput;
    }
    
    // Getters
    public SessionContext getSessionContext() { return sessionContext; }
    public String getSkillName() { return skillName; }
    public String getUserInput() { return userInput; }
}