package com.codemind.impl.skill;

import com.codemind.api.llm.LLMClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;

/**
 * Skill 路由器（Facade - 支持语义路由 + 关键词降级）
 * 
 * 职责：
 * - 检查用户输入是否匹配某个 Skill 的触发关键词
 * - 支持 LLM 语义判断（SemanticSkillRouter）
 * - 降级到关键词匹配（KeywordSkillRouter）
 * 
 * 设计原则：
 * - 语义优先：先尝试 LLM 判断用户意图
 * - 降级保证：LLM 不可用时自动降级到关键词匹配
 * - 可观测：路由决策可追踪（RouteReason）
 */
public class SkillRouter {
    
    private static final Logger log = LoggerFactory.getLogger(SkillRouter.class);
    
    private final SemanticSkillRouter semanticRouter;
    
    /**
     * 创建 SkillRouter（使用语义路由 + 关键词降级）
     */
    public SkillRouter(LLMClient llmClient, List<SkillDefinition> skills) {
        this.semanticRouter = new SemanticSkillRouter(llmClient, skills);
    }
    
    /**
     * 路由用户输入到对应的 Skill
     * 
     * 算法：
     * 1. 尝试语义路由（LLM 判断意图）
     * 2. 如果失败，降级到关键词匹配
     * 
     * @param userInput 用户输入
     * @return 匹配的 Skill，如果没有匹配返回 null
     */
    public SkillRoute route(String userInput) {
        return semanticRouter.route(userInput);
    }
    
    /**
     * 获取所有 Skill 的简介（用于系统提示）
     */
    public String getAllSkillSummaries() {
        return semanticRouter.getAllSkillSummaries();
    }
    
    /**
     * 获取 Skill 数量
     */
    public int size() {
        return semanticRouter.getAllSkills().size();
    }
    
    /**
     * 获取所有 Skills
     */
    public List<SkillDefinition> getAllSkills() {
        return semanticRouter.getAllSkills();
    }
}