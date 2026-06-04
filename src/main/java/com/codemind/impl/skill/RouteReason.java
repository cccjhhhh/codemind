package com.codemind.impl.skill;

/**
 * Skill 路由原因
 * 
 * 用于追踪路由决策，便于调试和日志
 */
public enum RouteReason {
    /** 触发关键词匹配 */
    TRIGGER_KEYWORD,
    
    /** 禁用关键词匹配（跳过该 Skill） */
    DISABLED_KEYWORD,
    
    /** 显式调用（未来扩展：用户通过命令显式调用） */
    EXPLICIT_CALL,
    
    /** LLM 语义判断匹配 */
    LLM_INTENT
}
