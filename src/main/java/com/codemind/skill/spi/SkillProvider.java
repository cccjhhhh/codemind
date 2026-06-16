package com.codemind.skill.spi;

import com.codemind.skill.SkillEntry;
import java.util.List;

public interface SkillProvider {
    String name();
    List<SkillEntry> loadSkills();
}
