package com.codemind.api.skill;

import java.util.List;

/**
 * 技能注册中心接口
 */
public interface SkillRegistry {
    
    /**
     * 注册技能
     */
    void register(Skill skill);
    
    /**
     * 注销技能
     */
    void unregister(String name);
    
    /**
     * 获取技能
     */
    Skill get(String name);
    
    /**
     * 执行技能
     */
    SkillResult execute(String name, SkillContext context);
    
    /**
     * 获取所有技能名称
     */
    List<String> getAllNames();
    
    /**
     * 检查技能是否存在
     */
    boolean hasSkill(String name);
}