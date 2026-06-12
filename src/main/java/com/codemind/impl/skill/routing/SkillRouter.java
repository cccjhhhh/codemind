package com.codemind.impl.skill.routing;

import com.codemind.dto.skill.SkillRouteDto;
import com.codemind.api.skill.SkillDefinition;

import java.util.List;

public class SkillRouter {
    private final KeywordSkillRouter keywordRouter;

    public SkillRouter(List<SkillDefinition> skills) {
        this.keywordRouter = new KeywordSkillRouter(skills);
    }

    public SkillRouteDto route(String userInput) {
        return keywordRouter.route(userInput);
    }

    public List<SkillDefinition> getAllSkills() {
        return keywordRouter.getAllSkills();
    }

    public String getAllSkillSummaries() {
        return keywordRouter.getAllSkillSummaries();
    }

    public int size() {
        return keywordRouter.getAllSkills().size();
    }
}
