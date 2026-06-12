package com.codemind.api.skill;

import com.codemind.api.skill.SkillMetadata;
import java.util.List;

/**
 * Skill definition — metadata container.
 * No executor. LLM reads the metadata.fullContent and follows instructions.
 */
public class SkillDefinition {

    private final SkillMetadata metadata;

    public SkillDefinition(SkillMetadata metadata) {
        if (metadata == null) {
            throw new IllegalArgumentException("SkillMetadata cannot be null");
        }
        this.metadata = metadata;
    }

    public String matchesTrigger(String userInput) {
        String lowerInput = userInput.toLowerCase();
        for (String keyword : metadata.triggerKeywords()) {
            if (lowerInput.contains(keyword.toLowerCase())) {
                return keyword;
            }
        }
        return null;
    }

    public boolean matchesDisabled(String userInput) {
        String lowerInput = userInput.toLowerCase();
        for (String keyword : metadata.disabledKeywords()) {
            if (lowerInput.contains(keyword.toLowerCase())) {
                return true;
            }
        }
        return false;
    }

    public String getName() { return metadata.name(); }
    public String getDescription() { return metadata.description(); }
    public List<String> getTriggerKeywords() { return metadata.triggerKeywords(); }
    public List<String> getDisabledKeywords() { return metadata.disabledKeywords(); }
    public List<String> getAllowedTools() { return metadata.allowedTools(); }
    public SkillMetadata getMetadata() { return metadata; }
    public String getFullContent() { return metadata.fullContent(); }
}
