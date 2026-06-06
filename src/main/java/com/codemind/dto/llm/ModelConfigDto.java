package com.codemind.dto.llm;

/**
 * 模型配置
 * 
 * 存储单个模型的配置信息
 */
public class ModelConfigDto {
    
    private String id;           // 模型标识符：deepseek, gpt
    private String name;        // 显示名称：DeepSeek, GPT-4
    private String type;        // 类型：openai_compatible, anthropic
    private String baseUrl;     // API 地址
    private String defaultModel; // 默认模型名
    private String apiKey;       // API Key
    
    public ModelConfigDto() {}
    
    public ModelConfigDto(String id, String name, String type, String baseUrl, String defaultModel, String apiKey) {
        this.id = id;
        this.name = name;
        this.type = type;
        this.baseUrl = baseUrl;
        this.defaultModel = defaultModel;
        this.apiKey = apiKey;
    }
    
    // Getters
    public String getId() { return id; }
    public String getName() { return name; }
    public String getType() { return type; }
    public String getBaseUrl() { return baseUrl; }
    public String getDefaultModel() { return defaultModel; }
    public String getApiKey() { return apiKey; }
    
    // Setters
    public void setId(String id) { this.id = id; }
    public void setName(String name) { this.name = name; }
    public void setType(String type) { this.type = type; }
    public void setBaseUrl(String baseUrl) { this.baseUrl = baseUrl; }
    public void setDefaultModel(String defaultModel) { this.defaultModel = defaultModel; }
    public void setApiKey(String apiKey) { this.apiKey = apiKey; }
}
