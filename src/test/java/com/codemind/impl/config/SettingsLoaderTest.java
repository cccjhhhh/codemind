package com.codemind.impl.config;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("SettingsLoader")
class SettingsLoaderTest {

    @TempDir
    Path tempDir;

    @Test
    @DisplayName("loads empty settings when no config files exist")
    void loadsDefaultsWhenNoFiles() {
        Settings s = SettingsLoader.loadChain(tempDir);
        assertNotNull(s);
        assertTrue(s.getSkillDirectories().isEmpty());
    }

    @Test
    @DisplayName("loads from project-level settings.json")
    void loadsFromProjectSettings() throws IOException {
        Path projectSettings = tempDir.resolve(".codemind/settings.json");
        Files.createDirectories(projectSettings.getParent());
        Files.writeString(projectSettings, """
            {
                "skillDirectories": [".claude/skills", "~/.codemind/skills"]
            }
            """);

        Settings s = SettingsLoader.loadChain(tempDir);
        assertEquals(2, s.getSkillDirectories().size());
        assertEquals(".claude/skills", s.getSkillDirectories().get(0));
    }

    @Test
    @DisplayName("local settings override project settings")
    void localOverridesProject() throws IOException {
        Files.createDirectories(tempDir.resolve(".codemind"));
        Files.writeString(tempDir.resolve(".codemind/settings.json"), """
            {"skillDirectories": ["from_project"]}
            """);
        Files.writeString(tempDir.resolve(".codemind/settings.local.json"), """
            {"skillDirectories": ["from_local"]}
            """);

        Settings s = SettingsLoader.loadChain(tempDir);
        assertEquals(1, s.getSkillDirectories().size());
        assertEquals("from_local", s.getSkillDirectories().get(0));
    }

    @Test
    @DisplayName("context config has sensible defaults")
    void contextHasDefaults() {
        Settings s = SettingsLoader.loadChain(tempDir);
        assertEquals(2000, s.getContext().getTruncation().getSpillThresholdChars());
        assertEquals(0.8, s.getContext().getWindow().getTargetRatio(), 0.001);
        assertEquals(5, s.getContext().getWindow().getStaleRounds());
    }

    @Test
    @DisplayName("global override parameter works")
    void globalOverrideParameter() throws IOException {
        Path global = tempDir.resolve("global_settings.json");
        Files.writeString(global, """
            {"skillDirectories": ["from_global"]}
            """);

        Settings s = SettingsLoader.loadChain(tempDir, global);
        assertEquals("from_global", s.getSkillDirectories().get(0));
    }
}
