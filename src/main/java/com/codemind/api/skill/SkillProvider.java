package com.codemind.api.skill;

import com.codemind.api.skill.SkillEntry;
import java.util.List;

public interface SkillProvider {
    String name();
    List<SkillEntry> loadSkills();
}
