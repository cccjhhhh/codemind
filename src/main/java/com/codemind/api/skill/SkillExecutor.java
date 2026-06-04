package com.codemind.api.skill;

/**
 * Skill 业务逻辑执行器
 * 
 * 职责：
 * - 执行具体的 Skill 业务逻辑
 * - 与 SkillDefinition 配合使用
 * 
 * 设计原则：
 * - 接口分离：Skill 接口用于 LLM Function Calling，SkillExecutor 用于内部执行
 * - 单一职责：只负责执行，不负责路由、加载等
 * 
 * 学习要点：
 * - 策略模式：不同的 Skill 有不同的执行策略
 * - 依赖注入：通过 SkillContext 注入依赖
 */
public interface SkillExecutor {
    
    /**
     * 执行 Skill 业务逻辑
     * 
     * @param context 执行上下文（包含 session、参数、toolRegistry）
     * @return 执行结果
     */
    SkillResult execute(SkillContext context);
    
    /**
     * 获取元数据（可选实现）
     * 
     * 默认实现返回 null，由 SkillDefinition 从 SKILL.md 加载
     */
    default SkillMetadata getMetadata() {
        return null;
    }
}
