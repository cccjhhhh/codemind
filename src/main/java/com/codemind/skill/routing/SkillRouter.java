package com.codemind.skill.routing;

import com.codemind.skill.SkillRouteDto;
import com.codemind.skill.SkillDefinition;

import java.util.List;

public class SkillRouter {
    private final KeywordSkillRouter keywordRouter;

    public SkillRouter(List<SkillDefinition> skills) {
        this.keywordRouter = new KeywordSkillRouter(skills);
    }

    public SkillRouteDto route(String userInput) {
        return keywordRouter.route(userInput);
    }
}
