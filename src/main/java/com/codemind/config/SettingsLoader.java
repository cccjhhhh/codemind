package com.codemind.config;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

public class SettingsLoader {
    private static final Logger log = LoggerFactory.getLogger(SettingsLoader.class);
    private static final ObjectMapper JSON = new ObjectMapper()
        .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);

    /**
     * Load config chain: global ~/.codemind/settings.json -> project .codemind/settings.json -> project .codemind/settings.local.json
     * Later files override earlier ones.
     */
    public static Settings loadChain(Path projectDir) {
        Settings settings = new Settings();
        Path global = Path.of(System.getProperty("user.home"), ".codemind", "settings.json");
        loadInto(global, settings);
        Path project = projectDir.resolve(".codemind/settings.json");
        loadInto(project, settings);
        Path local = projectDir.resolve(".codemind/settings.local.json");
        loadInto(local, settings);
        return settings;
    }

    public static Settings loadChain(Path projectDir, Path globalOverride) {
        Settings settings = new Settings();
        loadInto(globalOverride, settings);
        Path project = projectDir.resolve(".codemind/settings.json");
        loadInto(project, settings);
        Path local = projectDir.resolve(".codemind/settings.local.json");
        loadInto(local, settings);
        return settings;
    }

    private static void loadInto(Path path, Settings target) {
        if (!Files.exists(path)) return;
        try {
            log.debug("Loading settings from: {}", path);
            Settings partial = JSON.readValue(path.toFile(), Settings.class);
            merge(target, partial);
        } catch (IOException e) {
            log.warn("Failed to load settings from {}: {}", path, e.getMessage());
        }
    }

    /**
     * Merge non-null fields from source into target.
     * For collection fields, source values replace target values (not append).
     */
    /**
     * 确保全局 settings.json 存在。如果不存在则创建包含默认模型配置的模板。
     * 在首次启动时调用，提供开箱即用的体验。
     */
    public static Path ensureGlobalConfig() {
        Path global = Path.of(System.getProperty("user.home"), ".codemind", "settings.json");
        if (Files.exists(global)) return global;
        try {
            Files.createDirectories(global.getParent());
            String template = """
                {
                    "models": {
                        "deepseek": {
                            "name": "DeepSeek",
                            "type": "openai_compatible",
                            "baseUrl": "https://api.deepseek.com/v1",
                            "defaultModel": "deepseek-chat",
                            "apiKey": "YOUR_DEEPSEEK_API_KEY"
                        },
                        "gpt": {
                            "name": "GPT-4o",
                            "type": "openai_compatible",
                            "baseUrl": "https://api.openai.com/v1",
                            "defaultModel": "gpt-4o",
                            "apiKey": "YOUR_OPENAI_API_KEY"
                        }
                    },
                    "currentModel": "deepseek"
                }
                """;
            Files.writeString(global, template);
            log.info("已创建默认全局配置: {}", global);
        } catch (IOException e) {
            log.warn("创建默认全局配置失败: {}", e.getMessage());
        }
        return global;
    }

    static void merge(Settings target, Settings source) {
        if (source.getSkillDirectories() != null && !source.getSkillDirectories().isEmpty())
            target.setSkillDirectories(source.getSkillDirectories());
        if (source.getSkillProviders() != null && !source.getSkillProviders().isEmpty())
            target.setSkillProviders(source.getSkillProviders());
        // Merge nested objects
        if (source.getPermissions() != null) {
            if (source.getPermissions().getRules() != null && !source.getPermissions().getRules().isEmpty())
                target.getPermissions().setRules(source.getPermissions().getRules());
            if (source.getPermissions().getDeny() != null && !source.getPermissions().getDeny().isEmpty())
                target.getPermissions().setDeny(source.getPermissions().getDeny());
        }
        if (source.getContext() != null) {
            if (source.getContext().getTruncation() != null) {
                if (source.getContext().getTruncation().getSpillThresholdChars() != 8000)
                    target.getContext().getTruncation().setSpillThresholdChars(source.getContext().getTruncation().getSpillThresholdChars());
                if (source.getContext().getTruncation().getSpillDir() != null)
                    target.getContext().getTruncation().setSpillDir(source.getContext().getTruncation().getSpillDir());
            }
            if (source.getContext().getWindow() != null) {
                if (source.getContext().getWindow().getTargetRatio() != 0.8)
                    target.getContext().getWindow().setTargetRatio(source.getContext().getWindow().getTargetRatio());
}
            if (source.getContext().getCompaction() != null) {
                if (source.getContext().getCompaction().getMaxMessagesBeforeSnip() != 50)
                    target.getContext().getCompaction().setMaxMessagesBeforeSnip(source.getContext().getCompaction().getMaxMessagesBeforeSnip());
                if (source.getContext().getCompaction().getKeepRecentToolResults() != 3)
                    target.getContext().getCompaction().setKeepRecentToolResults(source.getContext().getCompaction().getKeepRecentToolResults());
                if (source.getContext().getCompaction().getBudgetMaxBytes() != 200000)
                    target.getContext().getCompaction().setBudgetMaxBytes(source.getContext().getCompaction().getBudgetMaxBytes());
                if (source.getContext().getCompaction().getCompactOnRatio() != 0.9)
                    target.getContext().getCompaction().setCompactOnRatio(source.getContext().getCompaction().getCompactOnRatio());
                if (source.getContext().getCompaction().getMaxConsecutiveFailures() != 3)
                    target.getContext().getCompaction().setMaxConsecutiveFailures(source.getContext().getCompaction().getMaxConsecutiveFailures());
                if (source.getContext().getCompaction().isSaveTranscripts() != true)
                    target.getContext().getCompaction().setSaveTranscripts(source.getContext().getCompaction().isSaveTranscripts());
            }
        }
        if (source.getAgent() != null) {
            if (source.getAgent().getMaxIterations() != 50)
                target.getAgent().setMaxIterations(source.getAgent().getMaxIterations());
            if (source.getAgent().getTimeoutSeconds() != 300)
                target.getAgent().setTimeoutSeconds(source.getAgent().getTimeoutSeconds());
            if (source.getAgent().getSubtaskTimeoutSeconds() != 300)
                target.getAgent().setSubtaskTimeoutSeconds(source.getAgent().getSubtaskTimeoutSeconds());
        }
        // 模型配置整体替换（不逐 key 合并，避免残留旧模型）
        if (source.getModels() != null && !source.getModels().isEmpty())
            target.setModels(source.getModels());
        if (source.getCurrentModel() != null && !source.getCurrentModel().isEmpty())
            target.setCurrentModel(source.getCurrentModel());
    }
}
