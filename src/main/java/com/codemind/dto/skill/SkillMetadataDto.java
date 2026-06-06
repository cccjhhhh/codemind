package com.codemind.dto.skill;

import java.util.List;
import java.util.Map;

/**
 * Skill 元数据数据传输对象
 */
public record SkillMetadataDto(
    String name,
    String description,
    List<String> triggerKeywords,
    List<String> disabledKeywords,
    String fullContent,
    List<String> allowedTools,
    Map<String, Object> extras
) {
    public SkillMetadataDto {
        if (name == null || name.isBlank()) {
            throw new IllegalArgumentException("Skill name cannot be null or blank");
        }
        if (triggerKeywords == null) triggerKeywords = List.of();
        if (disabledKeywords == null) disabledKeywords = List.of();
        if (allowedTools == null) allowedTools = List.of();
        if (extras == null) extras = Map.of();
    }
    
    public String getSummary() {
        return name + ": " + description;
    }
}
