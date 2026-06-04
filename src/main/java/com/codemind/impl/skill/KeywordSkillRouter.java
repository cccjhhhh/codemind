package com.codemind.impl.skill;

import java.util.List;

/**
 * 关键词 Skill 路由器（确定性匹配 - Tier 2）
 * 
 * 工作原理：
 * 1. 先检查否定关键词 → 直接返回 null（不触发任何 Skill）
 * 2. 再检查禁用关键词 → 跳过该 Skill
 * 3. 最后检查触发关键词 → 返回第一个匹配
 * 
 * 设计原则：
 * - 确定性：相同输入 → 相同输出
 * - 优先级：按注册顺序匹配
 * - 否定优先：否定关键词优先级最高
 * 
 * 参考：Claude Code "DO NOT trigger on" pattern
 */
public class KeywordSkillRouter {
    
    /** 否定关键词：包含这些词时不触发任何 Skill */
    private static final List<String> NEGATIVE_INDICATORS = List.of(
        "看看", "聊聊", "问一下", "解释下", "帮我看看", 
        "有什么想法", "你觉得", "怎么说", "帮我理解",
        "只是问", "问个问题", "随便聊聊", "解释一下"
    );
    
    private final List<SkillDefinition> skills;
    
    public KeywordSkillRouter(List<SkillDefinition> skills) {
        this.skills = skills != null ? skills : List.of();
    }
    
    /**
     * 路由用户输入到对应的 Skill
     * 
     * 算法：
     * 0. 先检查否定关键词 → 返回 null（不触发任何 Skill）
     * 1. 再检查是否命中禁用关键词 → 跳过该 Skill
     * 2. 再检查是否命中触发关键词 → 返回路由结果
     * 3. 按注册顺序优先匹配第一个
     */
    public SkillRoute route(String userInput) {
        if (userInput == null || userInput.isBlank()) {
            return null;
        }
        
        String lowerInput = userInput.toLowerCase();
        
        // Step 0: 检查否定关键词（优先级最高）
        if (containsNegativeIndicator(lowerInput)) {
            // 包含否定关键词，不触发任何 Skill
            return null;
        }
        
        // Step 1-2: 检查 Skill 触发/禁用关键词
        for (SkillDefinition skill : skills) {
            // 检查禁用关键词
            if (skill.matchesDisabled(lowerInput)) {
                continue;
            }
            
            // 检查触发关键词
            String matchedKeyword = skill.matchesTrigger(lowerInput);
            if (matchedKeyword != null) {
                return SkillRoute.keywordMatch(skill, matchedKeyword);
            }
        }
        
        return null;
    }
    
    /**
     * 检查是否包含否定关键词
     * 
     * 否定关键词表示用户只是想聊天或提问，不想执行正式的 Skill
     */
    private boolean containsNegativeIndicator(String lowerInput) {
        for (String indicator : NEGATIVE_INDICATORS) {
            if (lowerInput.contains(indicator.toLowerCase())) {
                return true;
            }
        }
        return false;
    }
    
    /**
     * 获取所有否定关键词（用于调试/日志）
     */
    public static List<String> getNegativeIndicators() {
        return NEGATIVE_INDICATORS;
    }
    
    /**
     * 获取所有 Skill
     */
    public List<SkillDefinition> getAllSkills() {
        return skills;
    }
    
    /**
     * 获取所有 Skill 的简介
     */
    public String getAllSkillSummaries() {
        StringBuilder sb = new StringBuilder();
        sb.append("Available Skills:\n");
        for (SkillDefinition skill : skills) {
            sb.append("- ").append(skill.getMetadata().getSummary()).append("\n");
        }
        return sb.toString();
    }
}