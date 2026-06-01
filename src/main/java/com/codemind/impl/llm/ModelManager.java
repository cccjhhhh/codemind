package com.codemind.impl.llm;

import com.codemind.api.llm.*;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;

import java.io.File;
import java.io.IOException;
import java.util.*;

/**
 * 模型管理器
 * 
 * 管理模型配置，支持切换当前模型
 */
public class ModelManager {
    
    private static final String CONFIG_DIR = System.getProperty("user.home") + "/.codemind";
    private static final String MODELS_CONFIG = CONFIG_DIR + "/models.yml";
    
    private Map<String, ModelConfig> models;
    private String currentModelId;
    private final ObjectMapper yamlMapper;
    
    public ModelManager() {
        this.yamlMapper = new ObjectMapper(new YAMLFactory());
        this.models = new HashMap<>();
        loadModels();
    }
    
    /**
     * 加载模型配置
     */
    private void loadModels() {
        File configFile = new File(MODELS_CONFIG);
        
        if (!configFile.exists()) {
            // 创建默认配置
            createDefaultConfig();
        }
        
        try {
            Map<String, Object> config = yamlMapper.readValue(configFile, Map.class);
            
            // 加载所有模型
            Map<String, Map<String, Object>> modelsConfig = (Map<String, Map<String, Object>>) config.get("models");
            if (modelsConfig != null) {
                for (Map.Entry<String, Map<String, Object>> entry : modelsConfig.entrySet()) {
                    String id = entry.getKey();
                    Map<String, Object> modelData = entry.getValue();
                    
                    ModelConfig modelConfig = new ModelConfig();
                    modelConfig.setId(id);
                    modelConfig.setName((String) modelData.get("name"));
                    modelConfig.setType((String) modelData.getOrDefault("type", "openai_compatible"));
                    modelConfig.setBaseUrl((String) modelData.get("base_url"));
                    modelConfig.setDefaultModel((String) modelData.get("default_model"));
                    modelConfig.setApiKey((String) modelData.get("api_key"));
                    
                    models.put(id, modelConfig);
                }
            }
            
            // 加载当前模型
            currentModelId = (String) config.getOrDefault("current", "deepseek");
            
        } catch (IOException e) {
            throw new RuntimeException("加载模型配置失败: " + e.getMessage(), e);
        }
    }
    
    /**
     * 创建默认配置
     */
    private void createDefaultConfig() {
        // 确保目录存在
        new File(CONFIG_DIR).mkdirs();
        
        String defaultConfig = 
            "# CodeMind 模型配置\n" +
            "# 添加或修改模型配置\n\n" +
            "models:\n" +
            "  deepseek:\n" +
            "    name: DeepSeek\n" +
            "    type: openai_compatible\n" +
            "    base_url: https://api.deepseek.com/v1\n" +
            "    default_model: deepseek-chat\n" +
            "    api_key: YOUR_DEEPSEEK_API_KEY\n\n" +
            "  gpt:\n" +
            "    name: GPT-4\n" +
            "    type: openai_compatible\n" +
            "    base_url: https://api.openai.com/v1\n" +
            "    default_model: gpt-4\n" +
            "    api_key: YOUR_OPENAI_API_KEY\n\n" +
            "# 当前使用的模型\n" +
            "current: deepseek\n";
        
        try {
            java.nio.file.Files.writeString(new File(MODELS_CONFIG).toPath(), defaultConfig);
        } catch (IOException e) {
            throw new RuntimeException("创建默认配置失败", e);
        }
    }
    
    /**
     * 获取所有可用模型
     */
    public List<ModelConfig> listModels() {
        return new ArrayList<>(models.values());
    }
    
    /**
     * 获取当前模型
     */
    public ModelConfig getCurrentModel() {
        ModelConfig model = models.get(currentModelId);
        if (model == null) {
            // 找不到就返回第一个
            return models.values().iterator().next();
        }
        return model;
    }
    
    /**
     * 切换模型
     */
    public void switchModel(String modelId) {
        if (!models.containsKey(modelId)) {
            throw new IllegalArgumentException("未知的模型: " + modelId);
        }
        this.currentModelId = modelId;
        saveCurrentModel();
    }
    
    /**
     * 保存当前模型到配置
     */
    private void saveCurrentModel() {
        try {
            Map<String, Object> config = yamlMapper.readValue(new File(MODELS_CONFIG), Map.class);
            config.put("current", currentModelId);
            yamlMapper.writeValue(new File(MODELS_CONFIG), config);
        } catch (IOException e) {
            System.err.println("保存模型配置失败: " + e.getMessage());
        }
    }
    
    /**
     * 获取当前模型 ID
     */
    public String getCurrentModelId() {
        return currentModelId;
    }
}
