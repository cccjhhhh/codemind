package com.codemind.impl.skill;

import com.codemind.api.skill.Skill;
import com.codemind.api.skill.SkillContext;
import com.codemind.api.skill.SkillResult;
import com.codemind.impl.tool.ToolRegistry;
import java.util.Map;

/**
 * 技能注册中心
 * 
 * 管理所有可用技能的定义和执行
 */
public class SkillRegistry {
    
    private final ToolRegistry toolRegistry;
    private final Map<String, Skill> skills = new java.util.HashMap<>();
    
    public SkillRegistry(ToolRegistry toolRegistry) {
        this.toolRegistry = toolRegistry;
    }
    
    /**
     * 注册技能
     */
    public void register(Skill skill) {
        skills.put(skill.getName(), skill);
    }
    
    /**
     * 获取技能
     */
    public Skill get(String name) {
        return skills.get(name);
    }
    
    /**
     * 执行技能
     */
    public SkillResult execute(String name, SkillContext context) {
        Skill skill = skills.get(name);
        if (skill == null) {
            return SkillResult.failure("Skill not found: " + name);
        }
        try {
            return skill.execute(context);
        } catch (Exception e) {
            return SkillResult.failure("Skill execution failed: " + e.getMessage());
        }
    }
    
    /**
     * 获取所有技能名称
     */
    public java.util.Set<String> getSkillNames() {
        return java.util.Collections.unmodifiableSet(skills.keySet());
    }
}