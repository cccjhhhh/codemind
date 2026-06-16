package com.codemind.skill;

import com.codemind.skill.SkillEntry;
import com.codemind.skill.spi.SkillProvider;
import java.util.*;

public class SkillRegistry {
    private final List<SkillProvider> providers = new ArrayList<>();
    private final Map<String, SkillEntry> entries = new LinkedHashMap<>();

    public void addProvider(SkillProvider provider) {
        providers.add(provider);
    }

    public void refresh() {
        entries.clear();
        for (int i = providers.size() - 1; i >= 0; i--) {
            List<SkillEntry> loaded = providers.get(i).loadSkills();
            for (SkillEntry entry : loaded) {
                entries.put(entry.name(), entry);
            }
        }
    }

    public SkillEntry get(String name) { return entries.get(name); }
    public List<SkillEntry> listAll() { return List.copyOf(entries.values()); }
    public int size() { return entries.size(); }
}
