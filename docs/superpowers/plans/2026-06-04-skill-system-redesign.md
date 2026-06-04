# Skill 系统重新设计实现计划

> **状态：✅ 已完成（2026-06-04）**

**Goal:** 实现 Claude Code 风格的 Skill 系统，通过关键词硬匹配实现确定性触发，解决 LLM 直接调用 Tool 而不使用 Skill 的问题。

**Architecture:** 采用混合模式 - Java 类负责业务逻辑执行，SKILL.md 文件负责定义触发规则和元数据。Tool 是原子操作（LLM 自由选择），Skill 是多步流程（关键词触发）。渐进式加载节省 token。

**Tech Stack:** Java 17, Jackson (YAML 解析), SnakeYAML, 无外部 Agent 框架依赖

---

## 实现摘要

所有 Tasks 已完成。实际实现与计划有以下差异：

| 计划 | 实际实现 | 原因 |
|------|----------|------|
| 单一 `SkillRouter.java` | `KeywordSkillRouter.java` + `SemanticSkillRouter.java` | 职责分离更清晰 |
| `SkillRoute.java` 无 confidence | 新增 confidence 字段 + shouldExecute() 方法 | 支持置信度阈值判断 |
| `RouteReason.java` 3 个枚举 | 扩展为 7 个枚举（LLM_INTENT_HIGH/LOW, NEGATIVE_INDICATOR） | 更细粒度的路由追踪 |

---

## 文件结构

### 新增文件

```
src/main/java/com/codemind/
├── api/skill/
│   ├── SkillMetadata.java       # Skill 元数据 record（从 SKILL.md 解析）
│   └── SkillExecutor.java       # Skill 执行器接口
│
├── impl/skill/
│   ├── SkillDefinition.java     # Skill 定义（metadata + executor）
│   ├── SkillLoader.java         # 加载 SKILL.md + config.yaml
│   ├── SkillRouter.java         # 关键词匹配路由器
│   ├── SkillRoute.java          # 路由结果 record
│   └── RouteReason.java         # 路由原因枚举
│
└── resources/skills/
    ├── code_review/
    │   ├── SKILL.md             # 触发规则 + SOP
    │   └── config.yaml          # 依赖的 Tool 白名单
    │
    ├── generate_docs/
    │   ├── SKILL.md
    │   └── config.yaml
    │
    └── analyze_logs/
        ├── SKILL.md
        └── config.yaml
```

### 修改文件

| 文件 | 修改内容 |
|------|----------|
| `api/skill/Skill.java` | 添加 `getTriggerKeywords()` 默认方法 |
| `core/AgentLoop.java` | 添加 SkillRouter 硬路由逻辑（在 LLM 调用前检查） |
| `impl/bootstrap/AppBinder.java` | 使用 SkillLoader 加载 Skill，注入 SkillRouter |
| `impl/skill/SkillRegistryImpl.java` | 注册 SkillDefinition 而非 Skill |
| `impl/skill/SkillAsTool.java` | 标记为 @Deprecated，保留但不再主动使用 |
| `impl/skill/CodeReviewSkill.java` | 实现 SkillExecutor 接口 |
| `impl/skill/DocGenSkill.java` | 实现 SkillExecutor 接口 |
| `impl/skill/LogAnalysisSkill.java` | 实现 SkillExecutor 接口 |

---

## Task 1: 创建 SkillMetadata record

**Files:**
- Create: `src/main/java/com/codemind/api/skill/SkillMetadata.java`

- [ ] **Step 1: 创建 SkillMetadata record**

```java
package com.codemind.api.skill;

import java.util.List;
import java.util.Map;

/**
 * Skill 元数据（从 SKILL.md 解析）
 * 
 * 学习要点：
 * - Record 类型：不可变数据载体（Java 17+）
 * - 元数据与业务逻辑分离：易于序列化和缓存
 */
public record SkillMetadata(
    String name,                      // 唯一标识
    String description,               // 简介（用于阶段1渐进式加载）
    List<String> triggerKeywords,     // 触发关键词
    List<String> disabledKeywords,    // 禁用关键词
    String fullContent,               // 完整内容（SKILL.md body，用于阶段2）
    List<String> allowedTools,        // 允许调用的 Tool
    Map<String, Object> extras        // 额外配置
) {
    /**
     * 紧凑构造器：验证必填字段
     */
    public SkillMetadata {
        if (name == null || name.isBlank()) {
            throw new IllegalArgumentException("Skill name cannot be null or blank");
        }
        // 确保 lists 不为 null
        if (triggerKeywords == null) triggerKeywords = List.of();
        if (disabledKeywords == null) disabledKeywords = List.of();
        if (allowedTools == null) allowedTools = List.of();
        if (extras == null) extras = Map.of();
    }
    
    /**
     * 获取简介（用于路由决策，节省 token）
     */
    public String getSummary() {
        return name + ": " + description;
    }
}
```

- [ ] **Step 2: 验证编译**

Run: `mvn compile -q`
Expected: BUILD SUCCESS

- [ ] **Step 3: Commit**

```bash
git add src/main/java/com/codemind/api/skill/SkillMetadata.java
git commit -m "feat(skill): add SkillMetadata record for skill routing metadata"
```

---

## Task 2: 创建 SkillExecutor 接口

**Files:**
- Create: `src/main/java/com/codemind/api/skill/SkillExecutor.java`

- [ ] **Step 1: 创建 SkillExecutor 接口**

```java
package com.codemind.api.skill;

/**
 * Skill 业务逻辑执行器
 * 
 * 职责：
 * - 执行具体的 Skill 业务逻辑
 * - 与 SkillDefinition 配合使用
 * 
 * 设计原则：
 * - 接口分离：Skill 接口用于 LLM Function Calling，SkillExecutor 用于内部执行
 * - 单一职责：只负责执行，不负责路由、加载等
 * 
 * 学习要点：
 * - 策略模式：不同的 Skill 有不同的执行策略
 * - 依赖注入：通过 SkillContext 注入依赖
 */
public interface SkillExecutor {
    
    /**
     * 执行 Skill 业务逻辑
     * 
     * @param context 执行上下文（包含 session、参数、toolRegistry）
     * @return 执行结果
     */
    SkillResult execute(SkillContext context);
    
    /**
     * 获取元数据（可选实现）
     * 
     * 默认实现返回 null，由 SkillDefinition 从 SKILL.md 加载
     */
    default SkillMetadata getMetadata() {
        return null;
    }
}
```

- [ ] **Step 2: 验证编译**

Run: `mvn compile -q`
Expected: BUILD SUCCESS

- [ ] **Step 3: Commit**

```bash
git add src/main/java/com/codemind/api/skill/SkillExecutor.java
git commit -m "feat(skill): add SkillExecutor interface for skill business logic"
```

---

## Task 3: 创建 RouteReason 枚举

**Files:**
- Create: `src/main/java/com/codemind/impl/skill/RouteReason.java`

- [ ] **Step 1: 创建 RouteReason 枚举**

```java
package com.codemind.impl.skill;

/**
 * Skill 路由原因
 * 
 * 用于追踪路由决策，便于调试和日志
 */
public enum RouteReason {
    /** 触发关键词匹配 */
    TRIGGER_KEYWORD,
    
    /** 禁用关键词匹配（跳过该 Skill） */
    DISABLED_KEYWORD,
    
    /** 显式调用（未来扩展：用户通过命令显式调用） */
    EXPLICIT_CALL
}
```

- [ ] **Step 2: 验证编译**

Run: `mvn compile -q`
Expected: BUILD SUCCESS

- [ ] **Step 3: Commit**

```bash
git add src/main/java/com/codemind/impl/skill/RouteReason.java
git commit -m "feat(skill): add RouteReason enum for skill routing tracing"
```

---

## Task 4: 创建 SkillRoute record

**Files:**
- Create: `src/main/java/com/codemind/impl/skill/SkillRoute.java`

- [ ] **Step 1: 创建 SkillRoute record**

```java
package com.codemind.impl.skill;

/**
 * Skill 路由结果
 * 
 * 包含匹配的 Skill 定义和路由原因
 */
public record SkillRoute(
    SkillDefinition skill,
    RouteReason reason,
    String matchedKeyword   // 匹配的关键词（用于调试）
) {
    public SkillRoute {
        if (skill == null) {
            throw new IllegalArgumentException("SkillDefinition cannot be null");
        }
        if (reason == null) {
            reason = RouteReason.TRIGGER_KEYWORD;
        }
    }
}
```

- [ ] **Step 2: 验证编译**

Run: `mvn compile -q`
Expected: BUILD SUCCESS

- [ ] **Step 3: Commit**

```bash
git add src/main/java/com/codemind/impl/skill/SkillRoute.java
git commit -m "feat(skill): add SkillRoute record for routing result"
```

---

## Task 5: 创建 SkillDefinition 类

**Files:**
- Create: `src/main/java/com/codemind/impl/skill/SkillDefinition.java`

- [ ] **Step 1: 创建 SkillDefinition 类**

```java
package com.codemind.impl.skill;

import com.codemind.api.skill.SkillContext;
import com.codemind.api.skill.SkillExecutor;
import com.codemind.api.skill.SkillMetadata;
import com.codemind.api.skill.SkillResult;

import java.util.List;

/**
 * Skill 定义（包含元数据 + 执行器）
 * 
 * 职责：
 * - 持有 Skill 的元数据（从 SKILL.md 加载）
 * - 持有执行器（Java 实现）
 * - 提供关键词匹配方法
 * 
 * 设计原则：
 * - 单一职责：只负责"定义"，不负责"加载"或"路由"
 * - 不可变：创建后不可修改
 * 
 * 学习要点：
 * - 组合模式：metadata + executor
 * - 委托：execute 委托给 executor
 */
public class SkillDefinition {
    
    private final SkillMetadata metadata;
    private final SkillExecutor executor;
    
    public SkillDefinition(SkillMetadata metadata, SkillExecutor executor) {
        if (metadata == null) {
            throw new IllegalArgumentException("SkillMetadata cannot be null");
        }
        this.metadata = metadata;
        this.executor = executor;
    }
    
    /**
     * 执行 Skill
     */
    public SkillResult execute(SkillContext context) {
        if (executor == null) {
            return com.codemind.api.skill.SkillResult.failure(
                "No executor for skill: " + metadata.name());
        }
        return executor.execute(context);
    }
    
    /**
     * 检查用户输入是否匹配触发关键词
     * 
     * @param userInput 用户输入（已转小写）
     * @return 如果匹配，返回匹配的关键词；否则返回 null
     */
    public String matchesTrigger(String userInput) {
        String lowerInput = userInput.toLowerCase();
        for (String keyword : metadata.triggerKeywords()) {
            if (lowerInput.contains(keyword.toLowerCase())) {
                return keyword;
            }
        }
        return null;
    }
    
    /**
     * 检查用户输入是否包含禁用关键词
     * 
     * @param userInput 用户输入（已转小写）
     * @return 如果匹配，返回 true
     */
    public boolean matchesDisabled(String userInput) {
        String lowerInput = userInput.toLowerCase();
        for (String keyword : metadata.disabledKeywords()) {
            if (lowerInput.contains(keyword.toLowerCase())) {
                return true;
            }
        }
        return false;
    }
    
    // Getters - 委托给 metadata
    
    public String getName() {
        return metadata.name();
    }
    
    public String getDescription() {
        return metadata.description();
    }
    
    public List<String> getTriggerKeywords() {
        return metadata.triggerKeywords();
    }
    
    public List<String> getDisabledKeywords() {
        return metadata.disabledKeywords();
    }
    
    public List<String> getAllowedTools() {
        return metadata.allowedTools();
    }
    
    public SkillMetadata getMetadata() {
        return metadata;
    }
    
    public boolean hasExecutor() {
        return executor != null;
    }
}
```

- [ ] **Step 2: 验证编译**

Run: `mvn compile -q`
Expected: BUILD SUCCESS

- [ ] **Step 3: Commit**

```bash
git add src/main/java/com/codemind/impl/skill/SkillDefinition.java
git commit -m "feat(skill): add SkillDefinition class combining metadata and executor"
```

---

## Task 6: 创建 SkillLoader 类

**Files:**
- Create: `src/main/java/com/codemind/impl/skill/SkillLoader.java`

- [ ] **Step 1: 创建 SkillLoader 类**

```java
package com.codemind.impl.skill;

import com.codemind.api.skill.SkillExecutor;
import com.codemind.api.skill.SkillMetadata;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
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
     * 加载所有 Skill
     */
    public List<SkillDefinition> loadAll(Path skillsDir) throws IOException {
        if (!Files.exists(skillsDir)) {
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
     * 
     * 格式：
     * ---
     * name: code_review
     * description: ...
     * triggerKeywords:
     *   - 审查代码
     *   - review code
     * disabledKeywords:
     *   - 忽略审查
     * ---
     * 
     * ## SOP
     * ...
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
```

- [ ] **Step 2: 添加 Jackson YAML 依赖到 pom.xml**

在 `<dependencies>` 中添加：

```xml
<!-- Jackson YAML format -->
<dependency>
    <groupId>com.fasterxml.jackson.dataformat</groupId>
    <artifactId>jackson-dataformat-yaml</artifactId>
    <version>2.16.0</version>
</dependency>
```

- [ ] **Step 3: 验证编译**

Run: `mvn compile -q`
Expected: BUILD SUCCESS

- [ ] **Step 4: Commit**

```bash
git add src/main/java/com/codemind/impl/skill/SkillLoader.java pom.xml
git commit -m "feat(skill): add SkillLoader to parse SKILL.md and config.yaml"
```

---

## Task 7: 创建 SkillRouter 类

**Files:**
- Create: `src/main/java/com/codemind/impl/skill/SkillRouter.java`

- [ ] **Step 1: 创建 SkillRouter 类**

```java
package com.codemind.impl.skill;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;

/**
 * Skill 路由器（关键词硬匹配）
 * 
 * 职责：
 * - 检查用户输入是否匹配某个 Skill 的触发关键词
 * - 检查是否命中禁用关键词
 * - 返回路由结果（SkillDefinition + 原因）
 * 
 * 设计原则：
 * - 确定性触发：相同输入 → 相同路由结果
 * - 优先级：按注册顺序匹配（第一个命中）
 * - 可观测：路由决策可追踪
 * 
 * 学习要点：
 * - 策略模式：不同的路由策略（关键词、语义等）
 * - 责任链：按顺序检查每个 Skill
 */
public class SkillRouter {
    
    private static final Logger log = LoggerFactory.getLogger(SkillRouter.class);
    
    private final List<SkillDefinition> skills;
    
    public SkillRouter(List<SkillDefinition> skills) {
        this.skills = skills != null ? skills : List.of();
    }
    
    /**
     * 路由用户输入到对应的 Skill
     * 
     * 算法：
     * 1. 先检查是否命中禁用关键词 → 跳过该 Skill
     * 2. 再检查是否命中触发关键词 → 返回路由结果
     * 3. 按注册顺序优先匹配第一个
     * 
     * @param userInput 用户输入
     * @return 匹配的 Skill，如果没有匹配返回 null
     */
    public SkillRoute route(String userInput) {
        if (userInput == null || userInput.isBlank()) {
            return null;
        }
        
        String lowerInput = userInput.toLowerCase();
        
        for (SkillDefinition skill : skills) {
            // 1. 先检查禁用关键词
            if (skill.matchesDisabled(lowerInput)) {
                log.debug("Skill {} disabled by keyword in: {}", skill.getName(), userInput);
                continue;  // 跳过，明确禁用
            }
            
            // 2. 检查触发关键词
            String matchedKeyword = skill.matchesTrigger(lowerInput);
            if (matchedKeyword != null) {
                log.info("Routed to skill {} via keyword '{}' in: {}", 
                    skill.getName(), matchedKeyword, userInput);
                return new SkillRoute(skill, RouteReason.TRIGGER_KEYWORD, matchedKeyword);
            }
        }
        
        // 未匹配
        log.debug("No skill matched for input: {}", userInput);
        return null;
    }
    
    /**
     * 获取所有 Skill 的简介（用于系统提示）
     */
    public String getAllSkillSummaries() {
        StringBuilder sb = new StringBuilder();
        sb.append("Available Skills:\n");
        for (SkillDefinition skill : skills) {
            sb.append("- ").append(skill.getMetadata().getSummary()).append("\n");
        }
        return sb.toString();
    }
    
    /**
     * 获取 Skill 数量
     */
    public int size() {
        return skills.size();
    }
}
```

- [ ] **Step 2: 验证编译**

Run: `mvn compile -q`
Expected: BUILD SUCCESS

- [ ] **Step 3: Commit**

```bash
git add src/main/java/com/codemind/impl/skill/SkillRouter.java
git commit -m "feat(skill): add SkillRouter for keyword-based skill routing"
```

---

## Task 8: 更新 Skill.java 接口

**Files:**
- Modify: `src/main/java/com/codemind/api/skill/Skill.java`

- [ ] **Step 1: 添加 getTriggerKeywords() 默认方法**

```java
package com.codemind.api.skill;

import com.codemind.api.session.SessionContext;

import java.util.List;

/**
 * 技能接口
 * 
 * 技能是对复杂任务流程的封装，可由多个工具调用组合而成。
 * 学习要点：任务分解与编排、多工具协作、结果格式化
 */
public interface Skill {
    
    /**
     * 技能名称（唯一标识）
     */
    String getName();
    
    /**
     * 技能描述
     */
    String getDescription();
    
    /**
     * 执行技能
     * 
     * @param context 执行上下文
     * @return 技能执行结果
     */
    SkillResult execute(SkillContext context);
    
    /**
     * 获取触发关键词（用于硬路由）
     * 
     * 默认返回空列表，由 SKILL.md 定义
     * 
     * @return 触发关键词列表
     */
    default List<String> getTriggerKeywords() {
        return List.of();
    }
    
    /**
     * 获取禁用关键词（用于跳过触发）
     * 
     * 默认返回空列表，由 SKILL.md 定义
     * 
     * @return 禁用关键词列表
     */
    default List<String> getDisabledKeywords() {
        return List.of();
    }
}
```

- [ ] **Step 2: 验证编译**

Run: `mvn compile -q`
Expected: BUILD SUCCESS

- [ ] **Step 3: Commit**

```bash
git add src/main/java/com/codemind/api/skill/Skill.java
git commit -m "feat(skill): add getTriggerKeywords() default method to Skill interface"
```

---

## Task 9: 更新 AgentLoop 添加硬路由

**Files:**
- Modify: `src/main/java/com/codemind/core/AgentLoop.java`

- [ ] **Step 1: 添加 SkillRouter 字段和路由逻辑**

在 AgentLoop 类中：

```java
// 添加导入
import com.codemind.impl.skill.SkillRouter;
import com.codemind.impl.skill.SkillRoute;
import com.codemind.impl.skill.SkillDefinition;
import com.codemind.api.skill.SkillContext;

// 添加字段
private final SkillRouter skillRouter;

// 修改构造函数
public AgentLoop(LLMClient llmClient, ToolRegistry toolRegistry, 
                  PermissionGate permissionGate, OutputFormatter outputFormatter, 
                  int maxIterations, int maxExecutionTimeSeconds) {
    this.llmClient = llmClient;
    this.toolRegistry = toolRegistry;
    this.permissionGate = permissionGate;
    this.outputFormatter = outputFormatter;
    this.maxIterations = maxIterations;
    this.maxExecutionTimeMs = maxExecutionTimeSeconds > 0 ? maxExecutionTimeSeconds * 1000L : 0;
    this.skillRouter = null; // 默认为空，通过 setter 设置
}

// 添加新构造函数
public AgentLoop(LLMClient llmClient, ToolRegistry toolRegistry, 
                  PermissionGate permissionGate, OutputFormatter outputFormatter, 
                  int maxIterations, int maxExecutionTimeSeconds, 
                  SkillRouter skillRouter) {
    this.llmClient = llmClient;
    this.toolRegistry = toolRegistry;
    this.permissionGate = permissionGate;
    this.outputFormatter = outputFormatter;
    this.maxIterations = maxIterations;
    this.maxExecutionTimeMs = maxExecutionTimeSeconds > 0 ? maxExecutionTimeSeconds * 1000L : 0;
    this.skillRouter = skillRouter;
}

// 修改 runStream 方法开头，添加 Skill 路由检查
public AgentResult runStream(String input, SessionContext context, 
                              Consumer<String> outputHandler, PermissionPrompter prompter) {
    try {
        long startTime = System.currentTimeMillis();
        
        // ==================== 新增：Skill 硬路由检查 ====================
        if (skillRouter != null) {
            SkillRoute route = skillRouter.route(input);
            
            if (route != null) {
                // 命中 Skill：强制执行
                outputHandler.accept(outputFormatter.formatSkillStart(
                    route.skill().getName(), input));
                
                SkillDefinition skill = route.skill();
                
                // 构建 SkillContext
                SkillContext skillContext = new SkillContext(
                    context,  // SessionContext
                    skill.getName(),
                    input,
                    toolRegistry
                );
                
                // 执行 Skill
                com.codemind.api.skill.SkillResult skillResult = skill.execute(skillContext);
                
                outputHandler.accept(outputFormatter.formatSkillEnd(skillResult));
                
                // Skill 结果添加到历史
                context.addMessage(Message.user(input));
                context.addMessage(Message.skillResult(skillResult));
                
                // 让 LLM 继续处理（格式化/解释结果）
                // 继续原有流程，但不重复添加 user message
                return runLLMWithSkillResult(skillResult, context, outputHandler, prompter, startTime);
            }
        }
        // ==================== 结束 Skill 路由检查 ====================
        
        // 原有流程：LLM + Tools
        context.addMessage(Message.user(input));
        
        for (int iteration = 0; iteration < maxIterations; iteration++) {
            // ... 原有代码 ...
        }
        
    } catch (Exception e) {
        return AgentResult.failure("Agent 执行失败: " + e.getMessage());
    }
}

// 添加辅助方法：处理 Skill 结果后继续 LLM 流程
private AgentResult runLLMWithSkillResult(com.codemind.api.skill.SkillResult skillResult,
                                            SessionContext context,
                                            Consumer<String> outputHandler, 
                                            PermissionPrompter prompter,
                                            long startTime) {
    // 检查超时
    if (maxExecutionTimeMs > 0) {
        long elapsed = System.currentTimeMillis() - startTime;
        if (elapsed >= maxExecutionTimeMs) {
            return AgentResult.failure("执行超时");
        }
    }
    
    // 获取历史和工具
    List<Message> history = context.getManagedHistory();
    List<ToolDefinition> tools = toolRegistry.getAllDefinitions();
    
    // 调用 LLM 处理 Skill 结果
    AgentTurnResult turnResult = runSingleTurn(history, tools, outputHandler);
    
    // 添加 LLM 回复
    if (turnResult.hasToolCalls()) {
        context.addMessage(Message.assistantWithTools(turnResult.fullText, turnResult.toolCalls));
    } else {
        context.addMessage(Message.assistant(turnResult.fullText));
    }
    
    // 处理可能的工具调用
    if (turnResult.hasToolCalls()) {
        // 执行工具调用...
        for (ToolCall toolCall : turnResult.toolCalls) {
            // ... 原有工具执行逻辑 ...
        }
    }
    
    return AgentResult.success(turnResult.fullText);
}
```

- [ ] **Step 2: 在 OutputFormatter 接口添加 formatSkillStart/formatSkillEnd 方法**

```java
// 在 api/cli/OutputFormatter.java 添加
/**
 * 格式化 Skill 开始执行
 */
String formatSkillStart(String skillName, String input);

/**
 * 格式化 Skill 执行结束
 */
String formatSkillEnd(com.codemind.api.skill.SkillResult result);
```

- [ ] **Step 3: 在 DefaultOutputFormatter 实现新方法**

```java
@Override
public String formatSkillStart(String skillName, String input) {
    return AnsiStyles.formatSkillStart(skillName, input);
}

@Override
public String formatSkillEnd(com.codemind.api.skill.SkillResult result) {
    return AnsiStyles.formatSkillEnd(result);
}
```

- [ ] **Step 4: 在 AnsiStyles 添加格式化方法**

```java
// 在 impl/cli/AnsiStyles.java 添加
public static String formatSkillStart(String skillName, String input) {
    return CYAN + "⚡ Executing Skill: " + skillName + RESET + "\n" +
           DIM + "  Input: " + input + RESET + "\n";
}

public static String formatSkillEnd(com.codemind.api.skill.SkillResult result) {
    if (result.isSuccess()) {
        return GREEN + "✓ Skill completed" + RESET + "\n";
    } else {
        return RED + "✗ Skill failed: " + result.getError() + RESET + "\n";
    }
}
```

- [ ] **Step 5: 在 Message 类添加 skillResult 工厂方法**

```java
// 在 api/llm/Message.java 添加
/**
 * 创建 Skill 结果消息
 */
public static Message skillResult(com.codemind.api.skill.SkillResult result) {
    String content = result.isSuccess() 
        ? "Skill Result:\n" + result.getOutput()
        : "Skill Error: " + result.getError();
    return new Message(Role.TOOL, content, null, null);
}
```

- [ ] **Step 6: 验证编译**

Run: `mvn compile -q`
Expected: BUILD SUCCESS

- [ ] **Step 7: Commit**

```bash
git add src/main/java/com/codemind/core/AgentLoop.java \
        src/main/java/com/codemind/api/cli/OutputFormatter.java \
        src/main/java/com/codemind/impl/cli/DefaultOutputFormatter.java \
        src/main/java/com/codemind/impl/cli/AnsiStyles.java \
        src/main/java/com/codemind/api/llm/Message.java
git commit -m "feat(agent): add SkillRouter hard routing to AgentLoop"
```

---

## Task 10: 更新 AppBinder 使用 SkillLoader

**Files:**
- Modify: `src/main/java/com/codemind/impl/bootstrap/AppBinder.java`

- [ ] **Step 1: 添加 SkillLoader 和 SkillRouter 创建方法**

```java
// 添加导入
import com.codemind.impl.skill.*;
import java.nio.file.Path;
import java.nio.file.Paths;

// 在 AppBinder 类中添加方法

/**
 * 创建 SkillLoader
 */
public SkillLoader createSkillLoader() {
    return new SkillLoader();
}

/**
 * 加载并注册所有 Skill
 * 
 * @param skillLoader 加载器
 * @param skillsDir Skill 目录路径
 * @return 加载的 Skill 列表
 */
public List<SkillDefinition> loadSkills(SkillLoader skillLoader, String skillsDir) {
    try {
        Path skillsPath = Paths.get(skillsDir);
        return skillLoader.loadAll(skillsPath);
    } catch (IOException e) {
        throw new RuntimeException("Failed to load skills from: " + skillsDir, e);
    }
}

/**
 * 创建 SkillRouter
 */
public SkillRouter createSkillRouter(List<SkillDefinition> skills) {
    return new SkillRouter(skills);
}

/**
 * 注册 Skill Executor
 */
public void registerSkillExecutor(SkillLoader loader, String skillName, SkillExecutor executor) {
    loader.registerExecutor(skillName, executor);
}
```

- [ ] **Step 2: 验证编译**

Run: `mvn compile -q`
Expected: BUILD SUCCESS

- [ ] **Step 3: Commit**

```bash
git add src/main/java/com/codemind/impl/bootstrap/AppBinder.java
git commit -m "feat(bootstrap): add SkillLoader and SkillRouter creation to AppBinder"
```

---

## Task 11: 创建 code_review SKILL.md

**Files:**
- Create: `src/main/resources/skills/code_review/SKILL.md`

- [ ] **Step 1: 创建目录**

```bash
mkdir -p src/main/resources/skills/code_review
```

- [ ] **Step 2: 创建 SKILL.md**

```yaml
---
name: code_review
description: 对代码变更进行审查，发现潜在问题并提供改进建议
triggerKeywords:
  - 审查代码
  - review code
  - 检查代码
  - check code
  - 代码审查
  - code review
disabledKeywords:
  - 忽略审查
  - skip review
  - 跳过审查
---

# Code Review Skill

## 什么时候启用

当用户要求审查代码、检查变更、或在 git commit 前自动触发。

## 禁用场景

- 用户明确说"忽略审查"或"skip review"
- 没有代码变更（git diff 为空）

## 执行流程（SOP）

1. 执行 `git diff --cached` 获取暂存区变更
2. 如果暂存区为空，执行 `git diff` 获取工作区变更
3. 解析变更文件列表
4. 构建依赖图（解析 import 语句）
5. 查找受影响文件（BFS 遍历，最多 2 跳）
6. 读取变更文件和受影响文件内容
7. 计算风险评分
8. 生成审查报告

## 输出格式

返回 JSON 结构化数据，包含：
- `status`: 状态（success/no_changes/error）
- `changes`: 变更文件信息
- `affected`: 受影响文件信息
- `contents`: 文件内容预览
- `highRiskFiles`: 高风险文件列表
- `stats`: 统计信息

## 异常处理

- 如果 git 不可用：返回错误 "git 不可用，请确保在 git 仓库中执行"
- 如果依赖图构建失败：跳过依赖分析，只分析变更文件
- 如果文件读取失败：记录错误但继续分析其他文件

## 依赖的 Tools

- execute_command
- read_file
```

- [ ] **Step 3: Commit**

```bash
git add src/main/resources/skills/code_review/SKILL.md
git commit -m "feat(skill): add code_review SKILL.md with trigger keywords and SOP"
```

---

## Task 12: 创建 code_review config.yaml

**Files:**
- Create: `src/main/resources/skills/code_review/config.yaml`

- [ ] **Step 1: 创建 config.yaml**

```yaml
# Skill: code_review
name: code_review

# 依赖的 Tool 白名单（Skill 内部可以调用这些 Tool）
allowed_tools:
  - execute_command
  - read_file

# 版本信息
version: "1.0.0"

# 权限要求
permissions:
  - READ_FILE
  - EXECUTE_COMMAND
```

- [ ] **Step 2: Commit**

```bash
git add src/main/resources/skills/code_review/config.yaml
git commit -m "feat(skill): add code_review config.yaml with tool whitelist"
```

---

## Task 13: 创建 generate_docs SKILL.md

**Files:**
- Create: `src/main/resources/skills/generate_docs/SKILL.md`

- [ ] **Step 1: 创建目录和文件**

```yaml
---
name: generate_docs
description: 为代码生成文档，包括 API 文档、README 等
triggerKeywords:
  - 生成文档
  - generate docs
  - 写文档
  - 文档生成
  - doc gen
  - 生成 readme
disabledKeywords:
  - 跳过文档
  - skip docs
---

# Generate Docs Skill

## 什么时候启用

当用户要求生成文档、写 README、或为代码添加注释。

## 执行流程（SOP）

1. 扫描指定目录的源文件
2. 解析类、方法、字段的结构
3. 提取现有注释
4. 根据代码逻辑生成文档
5. 格式化输出（Markdown）

## 依赖的 Tools

- read_file
- write_file
- execute_command
```

- [ ] **Step 2: Commit**

```bash
mkdir -p src/main/resources/skills/generate_docs
git add src/main/resources/skills/generate_docs/SKILL.md
git commit -m "feat(skill): add generate_docs SKILL.md"
```

---

## Task 14: 创建 generate_docs config.yaml

**Files:**
- Create: `src/main/resources/skills/generate_docs/config.yaml`

- [ ] **Step 1: 创建文件**

```yaml
name: generate_docs
allowed_tools:
  - read_file
  - write_file
  - execute_command
version: "1.0.0"
```

- [ ] **Step 2: Commit**

```bash
git add src/main/resources/skills/generate_docs/config.yaml
git commit -m "feat(skill): add generate_docs config.yaml"
```

---

## Task 15: 创建 analyze_logs SKILL.md

**Files:**
- Create: `src/main/resources/skills/analyze_logs/SKILL.md`

- [ ] **Step 1: 创建文件**

```yaml
---
name: analyze_logs
description: 分析日志文件，识别异常模式并生成报告
triggerKeywords:
  - 分析日志
  - analyze logs
  - 查看日志
  - 日志分析
  - parse logs
  - 检查日志
disabledKeywords:
  - 跳过日志
  - skip logs
---

# Analyze Logs Skill

## 什么时候启用

当用户要求分析日志、查看日志、或排查问题时。

## 执行流程（SOP）

1. 读取日志文件
2. 解析日志格式（时间戳、级别、消息）
3. 按级别分类（ERROR、WARN、INFO、DEBUG）
4. 提取异常堆栈
5. 统计频率
6. 生成分析报告

## 输出格式

返回 JSON 结构化数据，包含：
- `summary`: 摘要统计
- `errors`: 错误列表
- `warnings`: 警告列表
- `patterns`: 发现的模式

## 依赖的 Tools

- read_file
- execute_command
```

- [ ] **Step 2: Commit**

```bash
mkdir -p src/main/resources/skills/analyze_logs
git add src/main/resources/skills/analyze_logs/SKILL.md
git commit -m "feat(skill): add analyze_logs SKILL.md"
```

---

## Task 16: 创建 analyze_logs config.yaml

**Files:**
- Create: `src/main/resources/skills/analyze_logs/config.yaml`

- [ ] **Step 1: 创建文件**

```yaml
name: analyze_logs
allowed_tools:
  - read_file
  - execute_command
version: "1.0.0"
```

- [ ] **Step 2: Commit**

```bash
git add src/main/resources/skills/analyze_logs/config.yaml
git commit -m "feat(skill): add analyze_logs config.yaml"
```

---

## Task 17: 重构 CodeReviewSkill 实现 SkillExecutor

**Files:**
- Modify: `src/main/java/com/codemind/impl/skill/CodeReviewSkill.java`

- [ ] **Step 1: 修改类声明**

```java
// 修改类声明
public class CodeReviewSkill implements SkillExecutor {  // 改为实现 SkillExecutor
    
    // ... 原有字段和方法保持不变 ...
    
    // 移除 getName() 和 getDescription() 方法（不再需要）
    
    // 保持 execute() 方法签名不变
    @Override
    public SkillResult execute(SkillContext context) {
        // ... 原有实现 ...
    }
}
```

- [ ] **Step 2: 验证编译**

Run: `mvn compile -q`
Expected: BUILD SUCCESS

- [ ] **Step 3: Commit**

```bash
git add src/main/java/com/codemind/impl/skill/CodeReviewSkill.java
git commit -m "refactor(skill): CodeReviewSkill implements SkillExecutor instead of Skill"
```

---

## Task 18: 重构 DocGenSkill 实现 SkillExecutor

**Files:**
- Modify: `src/main/java/com/codemind/impl/skill/DocGenSkill.java`

- [ ] **Step 1: 修改类声明**

```java
public class DocGenSkill implements SkillExecutor {
    // ... 实现 SkillExecutor 接口 ...
}
```

- [ ] **Step 2: Commit**

```bash
git add src/main/java/com/codemind/impl/skill/DocGenSkill.java
git commit -m "refactor(skill): DocGenSkill implements SkillExecutor"
```

---

## Task 19: 重构 LogAnalysisSkill 实现 SkillExecutor

**Files:**
- Modify: `src/main/java/com/codemind/impl/skill/LogAnalysisSkill.java`

- [ ] **Step 1: 修改类声明**

```java
public class LogAnalysisSkill implements SkillExecutor {
    // ... 实现 SkillExecutor 接口 ...
}
```

- [ ] **Step 2: Commit**

```bash
git add src/main/java/com/codemind/impl/skill/LogAnalysisSkill.java
git commit -m "refactor(skill): LogAnalysisSkill implements SkillExecutor"
```

---

## Task 20: 标记 SkillAsTool 为废弃

**Files:**
- Modify: `src/main/java/com/codemind/impl/skill/SkillAsTool.java`

- [ ] **Step 1: 添加 @Deprecated 注解和注释**

```java
/**
 * Skill 包装成 Tool 的适配器
 * 
 * @deprecated 使用新的 SkillRouter 系统代替。
 *             Skill 现在通过关键词硬匹配触发，不再需要包装成 Tool。
 *             保留此类是为了向后兼容，未来版本可能移除。
 * 
 * @see SkillRouter
 * @see SkillDefinition
 */
@Deprecated(since = "2026-06-04", forRemoval = true)
public class SkillAsTool implements Tool {
    // ... 保持原有实现不变 ...
}
```

- [ ] **Step 2: Commit**

```bash
git add src/main/java/com/codemind/impl/skill/SkillAsTool.java
git commit -m "refactor(skill): deprecate SkillAsTool in favor of SkillRouter"
```

---

## Task 21: 更新旧设计文档

**Files:**
- Modify: `docs/superpowers/specs/2026-06-03-skill-architecture-design.md`

- [ ] **Step 1: 添加废弃声明**

在文件开头添加：

```markdown
# Skill 系统架构设计（方案 A）

> 日期：2026-06-03
> 状态：⚠️ **已废弃 - 被 2026-06-04-skill-system-redesign.md 取代**
> 废弃原因：SkillAsTool 模式导致 LLM 无法区分 Tool 和 Skill，LLM 倾向直接调用 Tool

## ⚠️ 重要说明

本文档描述的设计已被新设计取代：

- **新设计文档**：`2026-06-04-skill-system-redesign.md`
- **核心变化**：
  - 从 SkillAsTool 包装模式 → SkillRouter 关键词硬路由
  - 从隐式路由（依赖 LLM 推理）→ 显式路由（关键词匹配）
  - 新增 SKILL.md 文件定义触发规则

---

## 问题背景（历史记录，仅供参考）

... 原有内容保持不变 ...
```

- [ ] **Step 2: Commit**

```bash
git add docs/superpowers/specs/2026-06-03-skill-architecture-design.md
git commit -m "docs: mark 2026-06-03-skill-architecture-design as superseded"
```

---

## Task 22: 构建并验证

**Files:**
- None (构建验证)

- [ ] **Step 1: 完整构建**

Run: `mvn clean package -DskipTests`
Expected: BUILD SUCCESS

- [ ] **Step 2: 检查生成的 JAR**

Run: `ls -la target/codemind-*.jar`
Expected: JAR 文件存在

- [ ] **Step 3: 运行应用**

Run: `java -jar target/codemind-1.0.0-SNAPSHOT.jar --help`
Expected: 应用启动，显示帮助信息

- [ ] **Step 4: 最终 Commit**

```bash
git add -A
git commit -m "feat(skill): complete Skill system redesign with keyword-based routing

- Add SkillMetadata, SkillExecutor, SkillDefinition classes
- Add SkillLoader to parse SKILL.md and config.yaml
- Add SkillRouter for keyword-based hard routing
- Update AgentLoop with Skill routing logic
- Create SKILL.md files for code_review, generate_docs, analyze_logs
- Deprecate SkillAsTool in favor of new routing system
- Update documentation to reflect new architecture"
```

---

## 验收清单

- [ ] 用户说"审查代码" → 100% 触发 code_review Skill
- [ ] 用户说"分析日志" → 100% 触发 analyze_logs Skill
- [ ] 用户说"忽略审查" → code_review Skill 不触发
- [ ] SKILL.md 修改后无需重新编译
- [ ] 编译通过：`mvn clean package`
- [ ] 现有 Tool 调用不受影响（回归测试）

---

*计划版本：v1.0*
*创建日期：2026-06-04*
