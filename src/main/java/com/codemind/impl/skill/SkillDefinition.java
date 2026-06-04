package com.codemind.impl.skill;

import com.codemind.api.skill.SkillContext;
import com.codemind.api.skill.SkillExecutor;
import com.codemind.api.skill.SkillMetadata;
import com.codemind.api.skill.SkillResult;

import java.util.List;

/**
 * Skill 定义（包含元数据 + 执行器）
 * 
 * 职责：
 * - 持有 Skill 的元数据（从 SKILL.md 加载）
 * - 持有执行器（Java 实现）
 * - 提供关键词匹配方法
 * 
 * 设计原则：
 * - 单一职责：只负责"定义"，不负责"加载"或"路由"
 * - 不可变：创建后不可修改
 * 
 * 学习要点：
 * - 组合模式：metadata + executor
 * - 委托：execute 委托给 executor
 */
public class SkillDefinition {
    
    private final SkillMetadata metadata;
    private final SkillExecutor executor;
    
    public SkillDefinition(SkillMetadata metadata, SkillExecutor executor) {
        if (metadata == null) {
            throw new IllegalArgumentException("SkillMetadata cannot be null");
        }
        this.metadata = metadata;
        this.executor = executor;
    }
    
    /**
     * 执行 Skill
     */
    public SkillResult execute(SkillContext context) {
        if (executor == null) {
            return SkillResult.failure(
                "No executor for skill: " + metadata.name());
        }
        return executor.execute(context);
    }
    
    /**
     * 检查用户输入是否匹配触发关键词
     * 
     * @param userInput 用户输入（已转小写）
     * @return 如果匹配，返回匹配的关键词；否则返回 null
     */
    public String matchesTrigger(String userInput) {
        String lowerInput = userInput.toLowerCase();
        for (String keyword : metadata.triggerKeywords()) {
            if (lowerInput.contains(keyword.toLowerCase())) {
                return keyword;
            }
        }
        return null;
    }
    
    /**
     * 检查用户输入是否包含禁用关键词
     * 
     * @param userInput 用户输入（已转小写）
     * @return 如果匹配，返回 true
     */
    public boolean matchesDisabled(String userInput) {
        String lowerInput = userInput.toLowerCase();
        for (String keyword : metadata.disabledKeywords()) {
            if (lowerInput.contains(keyword.toLowerCase())) {
                return true;
            }
        }
        return false;
    }
    
    // Getters - 委托给 metadata
    
    public String getName() {
        return metadata.name();
    }
    
    public String getDescription() {
        return metadata.description();
    }
    
    public List<String> getTriggerKeywords() {
        return metadata.triggerKeywords();
    }
    
    public List<String> getDisabledKeywords() {
        return metadata.disabledKeywords();
    }
    
    public List<String> getAllowedTools() {
        return metadata.allowedTools();
    }
    
    public SkillMetadata getMetadata() {
        return metadata;
    }
    
    public boolean hasExecutor() {
        return executor != null;
    }
}
