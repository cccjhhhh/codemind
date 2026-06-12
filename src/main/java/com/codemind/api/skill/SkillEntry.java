package com.codemind.api.skill;

import com.codemind.api.skill.SkillMetadata;
import java.nio.file.Path;

public record SkillEntry(
    String name,
    String source,
    Path sourcePath,
    SkillMetadata metadata
) {}
