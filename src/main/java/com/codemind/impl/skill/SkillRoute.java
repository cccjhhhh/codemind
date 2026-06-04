package com.codemind.impl.skill;

/**
 * Skill 路由结果
 *
 * 包含匹配的 Skill 定义、路由原因和置信度
 * 
 * 设计原则：
 * - 置信度阈值：只有 confidence >= 0.7 才触发 Skill
 * - 否定指标：包含否定关键词时直接返回 null（不触发）
 * - 可观测性：所有路由决策都有 confidence 和 reason
 * 
 * 参考：LangChain tool selection threshold, Claude Code invocation control
 */
public record SkillRoute(
    SkillDefinition skill,
    RouteReason reason,
    String matchedKeyword,   // 匹配的关键词（用于调试）
    double confidence        // 置信度（0.0 - 1.0）
) {
    /** 默认置信度阈值（参考主流 Agent：LangChain 0.65, Claude 0.7） */
    public static final double CONFIDENCE_THRESHOLD = 0.7;
    
    public SkillRoute {
        if (skill == null) {
            throw new IllegalArgumentException("SkillDefinition cannot be null");
        }
        if (reason == null) {
            reason = RouteReason.TRIGGER_KEYWORD;
        }
        // 置信度范围检查
        if (confidence < 0.0 || confidence > 1.0) {
            confidence = 0.5;  // 默认中等置信度
        }
    }
    
    /**
     * 创建关键词匹配路由（置信度默认 1.0）
     */
    public static SkillRoute keywordMatch(SkillDefinition skill, String keyword) {
        return new SkillRoute(skill, RouteReason.TRIGGER_KEYWORD, keyword, 1.0);
    }
    
    /**
     * 创建语义匹配路由（带置信度）
     */
    public static SkillRoute semanticMatch(SkillDefinition skill, String reason, double confidence) {
        RouteReason routeReason = confidence >= CONFIDENCE_THRESHOLD 
            ? RouteReason.LLM_INTENT_HIGH 
            : RouteReason.LLM_INTENT_LOW;
        return new SkillRoute(skill, routeReason, reason, confidence);
    }
    
    /**
     * 判断是否应该执行 Skill
     * 
     * 规则：
     * 1. 精确匹配（EXPLICIT_CALL）→ 执行
     * 2. 关键词匹配（TRIGGER_KEYWORD）→ 执行
     * 3. 语义匹配 + 高置信度（LLM_INTENT_HIGH）→ 执行
     * 4. 其他 → 不执行，fallback 到 Chat
     */
    public boolean shouldExecute() {
        return reason == RouteReason.EXPLICIT_CALL 
            || reason == RouteReason.TRIGGER_KEYWORD
            || (reason == RouteReason.LLM_INTENT_HIGH && confidence >= CONFIDENCE_THRESHOLD);
    }
}
