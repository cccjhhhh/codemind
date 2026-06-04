package com.codemind.impl.skill;

import com.codemind.api.skill.SkillExecutor;
import com.codemind.api.skill.SkillMetadata;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 加载 SKILL.md 和 config.yaml，生成 SkillDefinition
 * 
 * 职责：
 * - 解析 SKILL.md 文件（YAML frontmatter + Markdown body）
 * - 解析 config.yaml 文件
 * - 查找对应的 Java Executor
 * 
 * 学习要点：
 * - 文件解析：自定义格式（YAML frontmatter）
 * - 工厂模式：创建 SkillDefinition
 * - 资源加载：从 classpath 加载
 */
public class SkillLoader {
    
    private static final Logger log = LoggerFactory.getLogger(SkillLoader.class);
    
    private static final ObjectMapper YAML_MAPPER = new ObjectMapper(new YAMLFactory());
    
    // YAML frontmatter 正则：--- 和 --- 之间的内容
    private static final Pattern FRONTMATTER_PATTERN = 
        Pattern.compile("^---\\s*\\n([\\s\\S]*?)\\n---\\s*\\n([\\s\\S]*)$", Pattern.MULTILINE);
    
    // Executor 注册表：name -> executor
    private final Map<String, SkillExecutor> executorRegistry;
    
    public SkillLoader() {
        this.executorRegistry = new HashMap<>();
    }
    
    /**
     * 注册 Executor
     */
    public void registerExecutor(String skillName, SkillExecutor executor) {
        executorRegistry.put(skillName, executor);
    }
    
    /**
     * 从目录加载单个 Skill
     */
    public SkillDefinition load(Path skillDir) throws IOException {
        String skillName = skillDir.getFileName().toString();
        
        // 1. 解析 SKILL.md
        Path skillMdPath = skillDir.resolve("SKILL.md");
        if (!Files.exists(skillMdPath)) {
            throw new IOException("SKILL.md not found in: " + skillDir);
        }
        
        SkillMetadata metadata = parseSkillMarkdown(skillMdPath);
        
        // 2. 解析 config.yaml（可选）
        Path configPath = skillDir.resolve("config.yaml");
        if (Files.exists(configPath)) {
            metadata = mergeConfig(metadata, configPath);
        }
        
        // 3. 查找对应的 Executor
        SkillExecutor executor = executorRegistry.get(skillName);
        if (executor == null) {
            log.warn("No executor registered for skill: {}, skill will be metadata-only", skillName);
        }
        
        return new SkillDefinition(metadata, executor);
    }
    
    /**
     * 从 classpath 加载所有 Skill（通用方案）
     * 
     * 自动扫描 classpath 中的 skills/ 目录
     * 适用于任何项目（IDE 开发环境或打包后的 JAR）
     * 
     * @param classLoader 类加载器，null 则使用当前线程的上下文类加载器
     * @return 加载的 Skill 列表
     */
    public List<SkillDefinition> loadAllFromClasspath(ClassLoader classLoader) {
        if (classLoader == null) {
            classLoader = Thread.currentThread().getContextClassLoader();
        }
        
        List<SkillDefinition> skills = new ArrayList<>();
        
        try {
            // 尝试从文件系统加载（IDE 环境）
            var resourceUrl = classLoader.getResource("skills");
            
            if (resourceUrl != null) {
                log.debug("Found skills resource: {}", resourceUrl);
                
                String protocol = resourceUrl.getProtocol();
                
                if ("file".equals(protocol)) {
                    // 文件系统（IDE 环境）
                    Path skillsPath = Path.of(resourceUrl.toURI());
                    skills.addAll(loadAll(skillsPath));
                } else if ("jar".equals(protocol)) {
                    // JAR 文件（打包后环境）
                    skills.addAll(loadFromJar(resourceUrl, classLoader));
                }
            } else {
                log.warn("Skills directory not found in classpath");
            }
        } catch (Exception e) {
            log.warn("Failed to load skills from classpath: {}", e.getMessage());
        }
        
        log.info("Loaded {} skills from classpath", skills.size());
        return skills;
    }
    
    /**
     * 从 JAR 文件加载 Skills
     */
    private List<SkillDefinition> loadFromJar(java.net.URL jarUrl, ClassLoader classLoader) throws IOException {
        List<SkillDefinition> skills = new ArrayList<>();
        
        // JAR URL 格式: jar:file:/path/to/app.jar!/skills
        String jarPath = jarUrl.getPath();
        int separatorIndex = jarPath.indexOf("!");
        if (separatorIndex > 0) {
            String filePath = jarPath.substring(0, separatorIndex);
            // 移除 file: 前缀
            if (filePath.startsWith("file:")) {
                filePath = filePath.substring(5);
            }
            
            try (var jarFile = new java.util.jar.JarFile(filePath)) {
                // 遍历 JAR 条目，查找 skills/ 目录下的 SKILL.md
                var entries = jarFile.entries();
                Set<String> skillNames = new java.util.HashSet<>();
                
                while (entries.hasMoreElements()) {
                    var entry = entries.nextElement();
                    String name = entry.getName();
                    
                    // 匹配 skills/<name>/SKILL.md
                    if (name.startsWith("skills/") && name.endsWith("/SKILL.md")) {
                        // 提取 skill 名称
                        String[] parts = name.split("/");
                        if (parts.length >= 3) {
                            skillNames.add(parts[1]);
                        }
                    }
                }
                
                // 加载每个 Skill
                for (String skillName : skillNames) {
                    try {
                        SkillDefinition skill = loadFromClasspath(skillName, classLoader);
                        if (skill != null) {
                            skills.add(skill);
                        }
                    } catch (Exception e) {
                        log.error("Failed to load skill: {}", skillName, e);
                    }
                }
            }
        }
        
        return skills;
    }
    
    /**
     * 从 classpath 加载单个 Skill
     */
    private SkillDefinition loadFromClasspath(String skillName, ClassLoader classLoader) throws IOException {
        // 1. 加载 SKILL.md
        String skillMdPath = "skills/" + skillName + "/SKILL.md";
        var skillMdUrl = classLoader.getResource(skillMdPath);
        
        if (skillMdUrl == null) {
            log.warn("SKILL.md not found for skill: {}", skillName);
            return null;
        }
        
        // 2. 读取 SKILL.md 内容
        String content;
        try (InputStream is = skillMdUrl.openStream()) {
            content = new String(is.readAllBytes(), java.nio.charset.StandardCharsets.UTF_8);
        }
        
        // 3. 解析 SKILL.md
        SkillMetadata metadata = parseSkillMarkdownContent(content);
        
        // 4. 尝试加载 config.yaml（可选）
        String configPath = "skills/" + skillName + "/config.yaml";
        var configUrl = classLoader.getResource(configPath);
        if (configUrl != null) {
            try (InputStream is = configUrl.openStream()) {
                String configContent = new String(is.readAllBytes(), java.nio.charset.StandardCharsets.UTF_8);
                metadata = mergeConfigContent(metadata, configContent);
            }
        }
        
        // 5. 查找对应的 Executor
        SkillExecutor executor = executorRegistry.get(skillName);
        if (executor == null) {
            log.warn("No executor registered for skill: {}, skill will be metadata-only", skillName);
        }
        
        return new SkillDefinition(metadata, executor);
    }
    
    /**
     * 解析 SKILL.md 内容字符串
     */
    private SkillMetadata parseSkillMarkdownContent(String content) throws IOException {
        Matcher matcher = FRONTMATTER_PATTERN.matcher(content);
        if (!matcher.matches()) {
            throw new IOException("Invalid SKILL.md format (missing frontmatter)");
        }
        
        String frontmatter = matcher.group(1);
        String body = matcher.group(2).trim();
        
        @SuppressWarnings("unchecked")
        Map<String, Object> yaml = YAML_MAPPER.readValue(frontmatter, Map.class);
        
        String name = getString(yaml, "name");
        String description = getString(yaml, "description");
        List<String> triggerKeywords = getList(yaml, "triggerKeywords");
        List<String> disabledKeywords = getList(yaml, "disabledKeywords");
        
        return new SkillMetadata(name, description, triggerKeywords, disabledKeywords, body, List.of(), Map.of());
    }
    
    /**
     * 合并 config.yaml 内容字符串
     */
    private SkillMetadata mergeConfigContent(SkillMetadata metadata, String configContent) throws IOException {
        @SuppressWarnings("unchecked")
        Map<String, Object> config = YAML_MAPPER.readValue(configContent, Map.class);
        
        List<String> allowedTools = getList(config, "allowed_tools");
        
        return new SkillMetadata(
            metadata.name(),
            metadata.description(),
            metadata.triggerKeywords(),
            metadata.disabledKeywords(),
            metadata.fullContent(),
            allowedTools,
            metadata.extras()
        );
    }
    
    /**
     * 从指定目录加载所有 Skill
     * 
     * @param skillsDir Skills 目录路径
     * @return 加载的 Skill 列表
     */
    public List<SkillDefinition> loadAll(Path skillsDir) throws IOException {
        if (skillsDir == null || !Files.exists(skillsDir)) {
            log.warn("Skills directory not found: {}", skillsDir);
            return List.of();
        }
        
        List<SkillDefinition> skills = new ArrayList<>();
        
        try (var stream = Files.list(skillsDir)) {
            stream.filter(Files::isDirectory)
                  .forEach(dir -> {
                      try {
                          skills.add(load(dir));
                      } catch (IOException e) {
                          log.error("Failed to load skill from: {}", dir, e);
                      }
                  });
        }
        
        log.info("Loaded {} skills from {}", skills.size(), skillsDir);
        return skills;
    }
    
    /**
     * 解析 SKILL.md 文件
     */
    @SuppressWarnings("unchecked")
    private SkillMetadata parseSkillMarkdown(Path skillMdPath) throws IOException {
        String content = Files.readString(skillMdPath);
        
        Matcher matcher = FRONTMATTER_PATTERN.matcher(content);
        if (!matcher.matches()) {
            throw new IOException("Invalid SKILL.md format (missing frontmatter): " + skillMdPath);
        }
        
        String frontmatter = matcher.group(1);
        String body = matcher.group(2).trim();
        
        // 解析 YAML frontmatter
        Map<String, Object> yaml = YAML_MAPPER.readValue(frontmatter, Map.class);
        
        String name = getString(yaml, "name");
        String description = getString(yaml, "description");
        List<String> triggerKeywords = getList(yaml, "triggerKeywords");
        List<String> disabledKeywords = getList(yaml, "disabledKeywords");
        
        return new SkillMetadata(
            name,
            description,
            triggerKeywords,
            disabledKeywords,
            body,  // fullContent = Markdown body
            List.of(),  // allowedTools 从 config.yaml 合并
            Map.of()
        );
    }
    
    /**
     * 合并 config.yaml 配置
     */
    @SuppressWarnings("unchecked")
    private SkillMetadata mergeConfig(SkillMetadata metadata, Path configPath) throws IOException {
        Map<String, Object> config = YAML_MAPPER.readValue(configPath.toFile(), Map.class);
        
        List<String> allowedTools = getList(config, "allowed_tools");
        
        return new SkillMetadata(
            metadata.name(),
            metadata.description(),
            metadata.triggerKeywords(),
            metadata.disabledKeywords(),
            metadata.fullContent(),
            allowedTools,
            metadata.extras()
        );
    }
    
    private String getString(Map<String, Object> map, String key) {
        Object value = map.get(key);
        return value != null ? value.toString() : "";
    }
    
    @SuppressWarnings("unchecked")
    private List<String> getList(Map<String, Object> map, String key) {
        Object value = map.get(key);
        if (value instanceof List) {
            return ((List<?>) value).stream()
                .map(Object::toString)
                .toList();
        }
        return List.of();
    }
}
