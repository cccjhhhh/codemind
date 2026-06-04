package com.codemind.impl.skill;

/**
 * Skill 路由结果
 *
 * 包含匹配的 Skill 定义和路由原因
 */
public record SkillRoute(
    SkillDefinition skill,
    RouteReason reason,
    String matchedKeyword   // 匹配的关键词（用于调试）
) {
    public SkillRoute {
        if (skill == null) {
            throw new IllegalArgumentException("SkillDefinition cannot be null");
        }
        if (reason == null) {
            reason = RouteReason.TRIGGER_KEYWORD;
        }
    }
}
