package com.codemind.impl.skill.routing;

import com.codemind.dto.skill.SkillRouteDto;
import com.codemind.impl.skill.SkillDefinition;

import java.util.List;

/**
 * 关键词 Skill 路由器（确定性匹配 - Tier 2）
 */
public class KeywordSkillRouter {
    
    private static final List<String> NEGATIVE_INDICATORS = List.of(
        "看看", "聊聊", "问一下", "解释下", "帮我看看", 
        "有什么想法", "你觉得", "怎么说", "帮我理解",
        "只是问", "问个问题", "随便聊聊", "解释一下"
    );
    
    private final List<SkillDefinition> skills;
    
    public KeywordSkillRouter(List<SkillDefinition> skills) {
        this.skills = skills != null ? skills : List.of();
    }
    
    public SkillRouteDto route(String userInput) {
        if (userInput == null || userInput.isBlank()) {
            return null;
        }
        
        String lowerInput = userInput.toLowerCase();
        
        if (containsNegativeIndicator(lowerInput)) {
            return null;
        }
        
        for (SkillDefinition skill : skills) {
            if (skill.matchesDisabled(lowerInput)) {
                continue;
            }
            
            String matchedKeyword = skill.matchesTrigger(lowerInput);
            if (matchedKeyword != null) {
                return SkillRouteDto.keywordMatch(skill, matchedKeyword);
            }
        }
        
        return null;
    }
    
    private boolean containsNegativeIndicator(String lowerInput) {
        for (String indicator : NEGATIVE_INDICATORS) {
            if (lowerInput.contains(indicator.toLowerCase())) {
                return true;
            }
        }
        return false;
    }
    
    public static List<String> getNegativeIndicators() {
        return NEGATIVE_INDICATORS;
    }
    
    public List<SkillDefinition> getAllSkills() {
        return skills;
    }
    
    public String getAllSkillSummaries() {
        StringBuilder sb = new StringBuilder();
        sb.append("Available Skills:\n");
        for (SkillDefinition skill : skills) {
            sb.append("- ").append(skill.getMetadata().getSummary()).append("\n");
        }
        return sb.toString();
    }
}
