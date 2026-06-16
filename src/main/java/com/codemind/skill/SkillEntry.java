package com.codemind.skill;

import com.codemind.skill.SkillMetadata;
import java.nio.file.Path;

public record SkillEntry(
    String name,
    String source,
    Path sourcePath,
    SkillMetadata metadata
) {}
