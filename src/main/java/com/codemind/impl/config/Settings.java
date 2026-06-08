package com.codemind.impl.config;

import com.codemind.api.llm.ModelConfig;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import java.util.List;
import java.util.Map;

@JsonIgnoreProperties(ignoreUnknown = true)
public class Settings {
    private List<String> skillDirectories = List.of();
    private List<String> skillProviders = List.of();
    private Permissions permissions = new Permissions();
    private ContextConfig context = new ContextConfig();
    private Map<String, ModelConfig> models = Map.of();
    private String currentModel = "";

    public List<String> getSkillDirectories() { return skillDirectories; }
    public void setSkillDirectories(List<String> skillDirectories) { this.skillDirectories = skillDirectories; }
    public List<String> getSkillProviders() { return skillProviders; }
    public void setSkillProviders(List<String> skillProviders) { this.skillProviders = skillProviders; }
    public Permissions getPermissions() { return permissions; }
    public void setPermissions(Permissions permissions) { this.permissions = permissions; }
    public ContextConfig getContext() { return context; }
    public void setContext(ContextConfig context) { this.context = context; }
    public Map<String, ModelConfig> getModels() { return models; }
    public void setModels(Map<String, ModelConfig> models) { this.models = models; }
    public String getCurrentModel() { return currentModel; }
    public void setCurrentModel(String currentModel) { this.currentModel = currentModel; }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class Permissions {
        private List<Rule> rules = List.of();
        private List<String> deny = List.of();

        public List<Rule> getRules() { return rules; }
        public void setRules(List<Rule> rules) { this.rules = rules; }
        public List<String> getDeny() { return deny; }
        public void setDeny(List<String> deny) { this.deny = deny; }
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class Rule {
        private String tool;
        private String condition;
        private String level;

        public String getTool() { return tool; }
        public void setTool(String tool) { this.tool = tool; }
        public String getCondition() { return condition; }
        public void setCondition(String condition) { this.condition = condition; }
        public String getLevel() { return level; }
        public void setLevel(String level) { this.level = level; }
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class ContextConfig {
        private Truncation truncation = new Truncation();
        private Window window = new Window();

        public Truncation getTruncation() { return truncation; }
        public void setTruncation(Truncation truncation) { this.truncation = truncation; }
        public Window getWindow() { return window; }
        public void setWindow(Window window) { this.window = window; }
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class Truncation {
        private int spillThresholdChars = 2000;
        private String spillDir = ".codemind/spill";

        public int getSpillThresholdChars() { return spillThresholdChars; }
        public void setSpillThresholdChars(int spillThresholdChars) { this.spillThresholdChars = spillThresholdChars; }
        public String getSpillDir() { return spillDir; }
        public void setSpillDir(String spillDir) { this.spillDir = spillDir; }
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class Window {
        private double targetRatio = 0.8;
        private int staleRounds = 5;

        public double getTargetRatio() { return targetRatio; }
        public void setTargetRatio(double targetRatio) { this.targetRatio = targetRatio; }
        public int getStaleRounds() { return staleRounds; }
        public void setStaleRounds(int staleRounds) { this.staleRounds = staleRounds; }
    }
}
