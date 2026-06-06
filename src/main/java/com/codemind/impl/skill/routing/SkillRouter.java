package com.codemind.impl.skill.routing;

import com.codemind.api.llm.LLMClient;
import com.codemind.impl.skill.SkillDefinition;
import com.codemind.dto.skill.SkillRouteDto;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;

/**
 * Skill 路由器（Facade - 支持语义路由 + 关键词降级）
 */
public class SkillRouter {
    
    private static final Logger log = LoggerFactory.getLogger(SkillRouter.class);
    
    private final SemanticSkillRouter semanticRouter;
    
    public SkillRouter(LLMClient llmClient, List<SkillDefinition> skills) {
        this.semanticRouter = new SemanticSkillRouter(llmClient, skills);
    }
    
    public SkillRouteDto route(String userInput) {
        return semanticRouter.route(userInput);
    }
    
    public String getAllSkillSummaries() {
        return semanticRouter.getAllSkillSummaries();
    }
    
    public int size() {
        return semanticRouter.getAllSkills().size();
    }
    
    public List<SkillDefinition> getAllSkills() {
        return semanticRouter.getAllSkills();
    }
}
