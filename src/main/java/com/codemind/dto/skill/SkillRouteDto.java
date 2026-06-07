package com.codemind.dto.skill;

import com.codemind.impl.skill.SkillDefinition;

public class SkillRouteDto {

    public static final double CONFIDENCE_THRESHOLD = 0.6;

    private final SkillDefinition skill;
    private final String matchedKeyword;
    private final double confidence;
    private final Source source;

    public enum Source {
        KEYWORD,
        SEMANTIC
    }

    public SkillRouteDto(SkillDefinition skill, String matchedKeyword, double confidence, Source source) {
        this.skill = skill;
        this.matchedKeyword = matchedKeyword;
        this.confidence = confidence;
        this.source = source;
    }

    public static SkillRouteDto keywordMatch(SkillDefinition skill, String keyword) {
        return new SkillRouteDto(skill, keyword, 1.0, Source.KEYWORD);
    }

    public static SkillRouteDto semanticMatch(SkillDefinition skill, String reason, double confidence) {
        return new SkillRouteDto(skill, reason, confidence, Source.SEMANTIC);
    }

    public boolean shouldExecute() {
        return confidence >= CONFIDENCE_THRESHOLD;
    }

    public SkillDefinition skill() { return skill; }
    public String matchedKeyword() { return matchedKeyword; }
    public String reason() { return matchedKeyword; }
    public double confidence() { return confidence; }
    public Source source() { return source; }
}
