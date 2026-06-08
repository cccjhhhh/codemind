package com.codemind.impl.llm;

import com.codemind.api.llm.ModelConfig;
import com.codemind.impl.config.Settings;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.IOException;
import java.nio.file.Path;
import java.util.*;

public class ModelManager {

    private static final Logger log = LoggerFactory.getLogger(ModelManager.class);
    private static final ObjectMapper JSON = new ObjectMapper()
        .enable(SerializationFeature.INDENT_OUTPUT);

    private final Map<String, ModelConfig> models = new LinkedHashMap<>();
    private String currentModelId;
    private final Path globalSettingsPath;

    /** 从 SettingsLoader 加载的配置构建 */
    public ModelManager(Settings settings) {
        this.globalSettingsPath = Path.of(System.getProperty("user.home"), ".codemind", "settings.json");
        initFromSettings(settings);
    }

    /** 兼容旧构造：直接指定路径 */
    public ModelManager(Settings settings, Path globalSettingsPath) {
        this.globalSettingsPath = globalSettingsPath;
        initFromSettings(settings);
    }

    private void initFromSettings(Settings settings) {
        models.clear();
        if (settings.getModels() != null) {
            for (var entry : settings.getModels().entrySet()) {
                ModelConfig mc = entry.getValue();
                mc.setId(entry.getKey());
                models.put(entry.getKey(), mc);
            }
        }
        currentModelId = settings.getCurrentModel();
        if (currentModelId == null || currentModelId.isBlank()) {
            currentModelId = models.isEmpty() ? "" : models.keySet().iterator().next();
        }
    }

    public List<ModelConfig> listModels() {
        return new ArrayList<>(models.values());
    }

    public ModelConfig getCurrentModel() {
        if (models.isEmpty()) {
            throw new IllegalStateException("No models configured. Add a model entry in " + globalSettingsPath);
        }
        ModelConfig model = models.get(currentModelId);
        if (model == null) {
            return models.values().iterator().next();
        }
        return model;
    }

    public void switchModel(String modelId) {
        if (!models.containsKey(modelId)) {
            throw new IllegalArgumentException("未知的模型: " + modelId
                + "，可用模型: " + models.keySet());
        }
        this.currentModelId = modelId;
        saveCurrentModel();
    }

    public String getCurrentModelId() {
        return currentModelId;
    }

    /** 将当前选中的模型 ID 写回全局 settings.json */
    private void saveCurrentModel() {
        try {
            File file = globalSettingsPath.toFile();
            Settings existing = file.exists()
                ? JSON.readValue(file, Settings.class)
                : new Settings();
            existing.setCurrentModel(currentModelId);
            JSON.writeValue(file, existing);
        } catch (IOException e) {
            log.error("保存当前模型到 settings.json 失败: {}", e.getMessage());
        }
    }
}
