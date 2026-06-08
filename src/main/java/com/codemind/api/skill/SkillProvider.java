package com.codemind.api.skill;

import com.codemind.impl.skill.SkillEntry;
import java.util.List;

public interface SkillProvider {
    String name();
    List<SkillEntry> loadSkills();
}
