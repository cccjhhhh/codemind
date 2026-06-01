package com.codemind.api.skill;

import com.codemind.api.session.SessionContext;

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
}