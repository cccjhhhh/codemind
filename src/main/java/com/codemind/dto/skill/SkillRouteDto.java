package com.codemind.dto.skill;

import com.codemind.impl.skill.SkillDefinition;

/**
 * Skill 路由结果数据传输对象
 */
public record SkillRouteDto(
    SkillDefinition skill,
    RouteReasonDto reason,
    String matchedKeyword,
    double confidence
) {
    public static final double CONFIDENCE_THRESHOLD = 0.7;
    
    public SkillRouteDto {
        if (skill == null) {
            throw new IllegalArgumentException("SkillDefinition cannot be null");
        }
        if (reason == null) {
            reason = RouteReasonDto.TRIGGER_KEYWORD;
        }
        if (confidence < 0.0 || confidence > 1.0) {
            confidence = 0.5;
        }
    }
    
    public static SkillRouteDto keywordMatch(SkillDefinition skill, String keyword) {
        return new SkillRouteDto(skill, RouteReasonDto.TRIGGER_KEYWORD, keyword, 1.0);
    }
    
    public static SkillRouteDto semanticMatch(SkillDefinition skill, String reason, double confidence) {
        RouteReasonDto routeReason = confidence >= CONFIDENCE_THRESHOLD 
            ? RouteReasonDto.LLM_INTENT_HIGH 
            : RouteReasonDto.LLM_INTENT_LOW;
        return new SkillRouteDto(skill, routeReason, reason, confidence);
    }
    
    public boolean shouldExecute() {
        return reason == RouteReasonDto.EXPLICIT_CALL 
            || reason == RouteReasonDto.TRIGGER_KEYWORD
            || (reason == RouteReasonDto.LLM_INTENT_HIGH && confidence >= CONFIDENCE_THRESHOLD);
    }
}
