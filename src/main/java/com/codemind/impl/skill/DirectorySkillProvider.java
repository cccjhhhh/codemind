package com.codemind.impl.skill;

import com.codemind.api.skill.SkillDefinition;
import com.codemind.api.skill.SkillEntry;
import com.codemind.api.skill.SkillProvider;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

public class DirectorySkillProvider implements SkillProvider {
    private static final Logger log = LoggerFactory.getLogger(DirectorySkillProvider.class);
    private final List<Path> directories;

    public DirectorySkillProvider(List<Path> directories) {
        this.directories = directories;
    }

    @Override
    public String name() { return "directory"; }

    @Override
    public List<SkillEntry> loadSkills() {
        List<SkillEntry> results = new ArrayList<>();
        SkillLoader loader = new SkillLoader();
        for (Path dir : directories) {
            if (!Files.isDirectory(dir)) {
                log.debug("Skill directory not found, skipping: {}", dir);
                continue;
            }
            try {
                List<SkillDefinition> defs = loader.loadAll(dir);
                for (SkillDefinition d : defs) {
                    Path skillDir = dir.resolve(d.getName());
                    results.add(new SkillEntry(d.getName(), dir.toString(), skillDir, d.getMetadata()));
                }
            } catch (Exception e) {
                log.warn("Failed to load skills from {}: {}", dir, e.getMessage());
            }
        }
        return results;
    }
}
