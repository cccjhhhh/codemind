package com.codemind.impl.skill;

/**
 * Skill 路由原因
 * 
 * 用于追踪路由决策，便于调试和日志
 * 
 * 设计原则：
 * - 分层路由：每种原因对应一个路由层级
 * - 可观测性：所有路由决策都有明确的 reason
 */
public enum RouteReason {
    // ========== Tier 1: 精确匹配层 ==========
    
    /** 用户通过 /slash 命令显式调用 */
    EXPLICIT_CALL,
    
    // ========== Tier 2: 关键词匹配层 ==========
    
    /** 触发关键词匹配（确定性） */
    TRIGGER_KEYWORD,
    
    /** 禁用关键词匹配（跳过该 Skill） */
    DISABLED_KEYWORD,
    
    // ========== Tier 3: 语义路由层 ==========
    
    /** LLM 语义判断匹配（高置信度） */
    LLM_INTENT_HIGH,    // confidence >= 0.7
    
    /** LLM 语义判断匹配（低置信度） */
    LLM_INTENT_LOW,     // confidence < 0.7
    
    // ========== 否定指标层 ==========
    
    /** 包含否定关键词（不触发任何 Skill） */
    NEGATIVE_INDICATOR,
    
    // ========== Fallback 层 ==========
    
    /** 无匹配，fallback 到普通 Chat */
    NO_MATCH_FALLBACK
}
