package com.codemind.impl.skill;

import com.codemind.api.skill.SkillDefinition;
import com.codemind.api.skill.SkillEntry;
import com.codemind.api.skill.SkillProvider;
import java.util.List;

public class ClasspathSkillProvider implements SkillProvider {
    private final ClassLoader classLoader;

    public ClasspathSkillProvider(ClassLoader classLoader) {
        this.classLoader = classLoader != null ? classLoader : Thread.currentThread().getContextClassLoader();
    }

    @Override
    public String name() { return "classpath"; }

    @Override
    public List<SkillEntry> loadSkills() {
        SkillLoader loader = new SkillLoader();
        List<SkillDefinition> defs = loader.loadAllFromClasspath(classLoader);
        return defs.stream()
            .map(d -> new SkillEntry(d.getName(), "classpath", null, d.getMetadata()))
            .toList();
    }
}
