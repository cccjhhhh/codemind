package com.codemind.api.skill;

import java.util.List;
import java.util.Map;

/**
 * Skill 元数据（从 SKILL.md 解析）
 * 
 * 学习要点：
 * - Record 类型：不可变数据载体（Java 17+）
 * - 元数据与业务逻辑分离：易于序列化和缓存
 */
public record SkillMetadata(
    String name,                      // 唯一标识
    String description,               // 简介（用于阶段1渐进式加载）
    List<String> triggerKeywords,     // 触发关键词
    List<String> disabledKeywords,    // 禁用关键词
    String fullContent,               // 完整内容（SKILL.md body，用于阶段2）
    List<String> allowedTools,        // 允许调用的 Tool
    Map<String, Object> extras        // 额外配置
) {
    /**
     * 紧凑构造器：验证必填字段
     */
    public SkillMetadata {
        if (name == null || name.isBlank()) {
            throw new IllegalArgumentException("Skill name cannot be null or blank");
        }
        // 确保 lists 不为 null
        if (triggerKeywords == null) triggerKeywords = List.of();
        if (disabledKeywords == null) disabledKeywords = List.of();
        if (allowedTools == null) allowedTools = List.of();
        if (extras == null) extras = Map.of();
    }
    
    /**
     * 获取简介（用于路由决策，节省 token）
     */
    public String getSummary() {
        return name + ": " + description;
    }
}
