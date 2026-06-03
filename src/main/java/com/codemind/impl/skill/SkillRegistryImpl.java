package com.codemind.impl.skill;

import com.codemind.api.skill.Skill;
import com.codemind.api.skill.SkillContext;
import com.codemind.api.skill.SkillRegistry;
import com.codemind.api.skill.SkillResult;
import com.codemind.api.tool.ToolRegistry;

import java.util.List;

/**
 * 技能注册中心实现
 * 
 * 管理所有可用技能的定义和执行
 */
public class SkillRegistryImpl implements SkillRegistry {
    
    private final ToolRegistry toolRegistry;
    private final java.util.Map<String, Skill> skills = new java.util.HashMap<>();
    
    public SkillRegistryImpl(ToolRegistry toolRegistry) {
        this.toolRegistry = toolRegistry;
    }
    
    @Override
    public void register(Skill skill) {
        skills.put(skill.getName(), skill);
    }
    
    @Override
    public void unregister(String name) {
        skills.remove(name);
    }
    
    @Override
    public Skill get(String name) {
        return skills.get(name);
    }
    
    @Override
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
    
    @Override
    public List<String> getAllNames() {
        return java.util.Collections.unmodifiableSet(skills.keySet()).stream().toList();
    }
    
    @Override
    public boolean hasSkill(String name) {
        return skills.containsKey(name);
    }
    
    /**
     * 获取所有技能名称（兼容旧API）
     */
    public java.util.Set<String> getSkillNames() {
        return java.util.Collections.unmodifiableSet(skills.keySet());
    }
}