package com.codemind.api.skill;

import java.util.List;

/**
 * 技能接口
 * 
 * 技能是对复杂任务流程的封装，可由多个工具调用组合而成。
 * 学习要点：任务分解与编排、多工具协作、结果格式化
 */
public interface Skill {
    
    /**
     * 技能名称（唯一标识）
     */
    String getName();
    
    /**
     * 技能描述
     */
    String getDescription();
    
    /**
     * 执行技能
     * 
     * @param context 执行上下文
     * @return 技能执行结果
     */
    SkillResult execute(SkillContext context);
    
    /**
     * 获取触发关键词（用于硬路由）
     * 
     * 默认返回空列表，由 SKILL.md 定义
     * 
     * @return 触发关键词列表
     */
    default List<String> getTriggerKeywords() {
        return List.of();
    }
    
    /**
     * 获取禁用关键词（用于跳过触发）
     * 
     * 默认返回空列表，由 SKILL.md 定义
     * 
     * @return 禁用关键词列表
     */
    default List<String> getDisabledKeywords() {
        return List.of();
    }
}