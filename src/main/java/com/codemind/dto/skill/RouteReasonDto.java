package com.codemind.dto.skill;

/**
 * Skill 路由原因枚举
 */
public enum RouteReasonDto {
    EXPLICIT_CALL,
    TRIGGER_KEYWORD,
    DISABLED_KEYWORD,
    LLM_INTENT_HIGH,
    LLM_INTENT_LOW,
    NEGATIVE_INDICATOR,
    NO_MATCH_FALLBACK
}
