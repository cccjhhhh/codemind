# Skill 系统重新设计

> 日期：2026-06-04
> 状态：待评审
> 决定人：[用户]

## 1. 背景与问题

### 1.1 当前问题

当前项目的 Skill 系统存在以下问题：

| 问题 | 描述 |
|------|------|
| **LLM 不使用 Skill** | Skill 被 SkillAsTool 包装后，与 Tool 在 LLM 看来完全一样，LLM 倾向直接调用 Tool |
| **描述重叠** | `parse_logs` (Tool) 和 `analyze_logs` (Skill) 功能完全一样 |
| **能力边界模糊** | Tool 和 Skill 没有明确的层级区分 |
| **维护困难** | Skill 规则写在 Java 代码里，改动需要重新编译部署 |
| **LangChain 模式失效** | 依赖 LLM "自己推理" 何时用 Skill，成功率低 |

### 1.2 根因分析

LangChain 的隐式路由模式（让 LLM 自己根据描述推理）在生产环境中成功率约 60-70%。当 Tool/Skill 超过 10 个且有功能重叠时，LLM 会保守选择更简单、token 消耗更少的 Tool。

Claude Code 的显式路由模式（关键词硬匹配 + Prompt 显式规则）在业界验证效果更好。

### 1.3 决定

采用 Claude Code 风格的 Skill 系统：
- **显式路由**：关键词硬匹配强制调用 Skill
- **渐进式加载**：Progressive Disclosure 节省 token
- **分层架构**：Tool（原子操作）和 Skill（多步流程）明确区分
- **文件化配置**：Skill 规则用 SKILL.md 定义，运行时可动态加载

---

## 2. 设计目标

| 目标 | 描述 |
|------|------|
| **确定性触发** | 用户说"审查代码"→ 100% 调用 `code_review` Skill |
| **能力边界清晰** | Tool 是原子操作，Skill 是多步流程，LLM 可区分 |
| **Token 高效** | Progressive Disclosure，只在需要时加载完整内容 |
| **易维护** | Skill 规则在文件中，改动不需重新编译 |
| **可观测** | 路由决策可追踪，出问题可定位 |

---

## 3. 架构设计

### 3.1 Tool vs Skill 分层

```
┌─────────────────────────────────────────────────────────────┐
│                        LLM 视角                              │
├─────────────────────────────────────────────────────────────┤
│  Skills（显式路由，关键词触发）                                │
│    - code_review                                           │
│    - generate_docs                                         │
│    - analyze_logs                                          │
│                                                             │
│  Tools（LLM 自由选择）                                       │
│    - read_file                                             │
│    - write_file                                            │
│    - execute_command                                       │
│    - search_code                                           │
└─────────────────────────────────────────────────────────────┘
```

| 类型 | 定义 | 触发方式 | 示例 |
|------|------|----------|------|
| **Tool** | 原子操作，单步执行 | LLM 根据描述自由选择 | `read_file`, `execute_command` |
| **Skill** | 多步流程，有业务含义 | **关键词硬匹配** | `code_review`, `generate_docs` |

### 3.2 Skill 目录结构

```
src/main/resources/skills/
├─ code_review/
│   ├─ SKILL.md        # 元数据 + 触发规则 + SOP + 异常处理
│   ├─ config.yaml     # 依赖的 Tool 白名单
│   └─ refs/           # 领域知识（RAG 数据源，可选）
│       └─ review_rules.md
│
├─ generate_docs/
│   ├─ SKILL.md
│   ├─ config.yaml
│   └─ refs/
│
└─ analyze_logs/
    ├─ SKILL.md
    ├─ config.yaml
    └─ refs/
```

### 3.3 SKILL.md 格式

```yaml
name: code_review
description: 对代码变更进行审查，发现潜在问题并提供改进建议
triggerKeywords:
  - 审查代码
  - review code
  - 检查变更
  - git commit
disabledKeywords:
  - 忽略审查
  - skip review

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

## 异常处理
- 如果 git 不可用：返回错误 "git 不可用，请确保在 git 仓库中执行"
- 如果依赖图构建失败：跳过依赖分析，只分析变更文件
- 如果文件读取失败：记录错误但继续分析其他文件

## 依赖的 Tools
- execute_command
- read_file
```

### 3.4 config.yaml 格式

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

### 3.5 渐进式加载（Progressive Disclosure）

```
阶段 1（启动时加载，极少 token）：
  Skill 名称 + 简介
  → 用于 LLM 做意图路由

阶段 2（命中后加载）：
  SKILL.md 完整内容 + SOP + 异常处理
  → 用于 Skill 执行

阶段 3（按需加载）：
  refs/ 目录下的领域知识
  → 用于 RAG 增强（如有）
```

---

## 4. 组件设计

### 4.1 Skill 元数据接口

```java
/**
 * Skill 元数据（从 SKILL.md 解析）
 */
public record SkillMetadata(
    String name,              // 唯一标识
    String description,       // 简介（用于阶段1）
    List<String> triggerKeywords,   // 触发关键词
    List<String> disabledKeywords, // 禁用关键词
    String fullContent,      // 完整内容（SKILL.md body，用于阶段2）
    List<String> allowedTools,     // 允许调用的 Tool
    Map<String, Object> extras     // 额外配置
) {}
```

### 4.2 SkillDefinition 类

```java
/**
 * Skill 定义（包含元数据 + Java 执行器）
 */
public class SkillDefinition {
    private final SkillMetadata metadata;
    private final SkillExecutor executor;  // Java 实现

    public SkillDefinition(SkillMetadata metadata, SkillExecutor executor) {
        this.metadata = metadata;
        this.executor = executor;
    }

    // Getters
    public String getName() { return metadata.name(); }
    public String getDescription() { return metadata.description(); }
    public List<String> getTriggerKeywords() { return metadata.triggerKeywords(); }
    public boolean matches(String userInput) { /* 关键词匹配逻辑 */ }
    public SkillResult execute(SkillContext context) {
        return executor.execute(context);
    }
}
```

### 4.3 SkillExecutor 接口

```java
/**
 * Skill 业务逻辑执行器
 */
public interface SkillExecutor {
    /**
     * 执行 Skill 业务逻辑
     */
    SkillResult execute(SkillContext context);

    /**
     * 获取元数据
     */
    default SkillMetadata getMetadata() {
        throw new UnsupportedOperationException();
    }
}
```

### 4.4 SkillLoader 类

```java
/**
 * 加载 SKILL.md 和 config.yaml，生成 SkillDefinition
 */
public class SkillLoader {

    /**
     * 从目录加载单个 Skill
     */
    public SkillDefinition load(Path skillDir) {
        // 1. 解析 SKILL.md 获取 metadata
        SkillMetadata metadata = parseSkillMarkdown(skillDir.resolve("SKILL.md"));

        // 2. 解析 config.yaml 获取 allowed_tools
        Config config = parseConfig(skillDir.resolve("config.yaml"));

        // 3. 合并配置
        metadata = mergeConfig(metadata, config);

        // 4. 查找对应的 Java Executor
        SkillExecutor executor = findExecutor(metadata.name());

        return new SkillDefinition(metadata, executor);
    }

    /**
     * 加载所有 Skill
     */
    public List<SkillDefinition> loadAll(Path skillsDir) {
        return Files.list(skillsDir)
            .filter(Files::isDirectory)
            .map(this::load)
            .toList();
    }
}
```

### 4.5 SkillRouter 类

```java
/**
 * Skill 路由器（关键词硬匹配）
 */
public class SkillRouter {

    private final List<SkillDefinition> skills;

    public SkillRouter(List<SkillDefinition> skills) {
        this.skills = skills;
    }

    /**
     * 路由用户输入到对应的 Skill
     *
     * @return 匹配的 Skill，如果没有匹配返回 null
     */
    public SkillRoute route(String userInput) {
        String lowerInput = userInput.toLowerCase();

        for (SkillDefinition skill : skills) {
            // 检查禁用关键词
            if (containsDisabledKeyword(lowerInput, skill)) {
                continue;  // 跳过，明确禁用
            }

            // 检查触发关键词
            if (containsTriggerKeyword(lowerInput, skill)) {
                return new SkillRoute(skill, RouteReason.TRIGGER_KEYWORD);
            }
        }

        return null;  // 未匹配
    }

    private boolean containsTriggerKeyword(String input, SkillDefinition skill) {
        for (String keyword : skill.getTriggerKeywords()) {
            if (input.contains(keyword.toLowerCase())) {
                return true;
            }
        }
        return false;
    }

    private boolean containsDisabledKeyword(String input, SkillDefinition skill) {
        for (String keyword : skill.getDisabledKeywords()) {
            if (input.contains(keyword.toLowerCase())) {
                return true;
            }
        }
        return false;
    }
}

public record SkillRoute(SkillDefinition skill, RouteReason reason) {}
public enum RouteReason { TRIGGER_KEYWORD, DISABLED_KEYWORD }
```

### 4.6 AgentLoop 改造

```java
/**
 * 改造后的 AgentLoop
 */
public AgentResult runStream(String input, SessionContext context,
                             Consumer<String> outputHandler,
                             PermissionPrompter prompter) {

    // 1. 检查是否命中 Skill（硬路由）
    SkillRoute route = skillRouter.route(input);

    if (route != null) {
        // 2a. 命中 Skill：强制执行 Skill
        outputHandler.accept(outputFormatter.formatSkillStart(
            route.skill().getName(), input));

        SkillContext skillContext = buildSkillContext(context, input);
        SkillResult result = route.skill().execute(skillContext);

        outputHandler.accept(outputFormatter.formatSkillEnd(result));

        // Skill 执行结果返回给 LLM 继续处理
        context.addMessage(Message.user(input));
        context.addMessage(Message.skillResult(result));
        
        // 让 LLM 格式化/解释结果
        return runLLMWithTools(context, tools);

    } else {
        // 2b. 未命中：走原有 LLM + Tools 流程
        return runLLMWithTools(input, tools, context);
    }
}
```

---

## 5. 文件变更清单

### 5.1 新增文件

| 文件 | 位置 | 说明 |
|------|------|------|
| `SkillMetadata.java` | `api/skill/` | Skill 元数据 record |
| `SkillDefinition.java` | `impl/skill/` | Skill 定义（metadata + executor） |
| `SkillExecutor.java` | `api/skill/` | Skill 执行器接口 |
| `SkillLoader.java` | `impl/skill/` | 加载 SKILL.md + config.yaml |
| `SkillRouter.java` | `impl/skill/` | 关键词匹配路由器 |
| `code_review/SKILL.md` | `resources/skills/` | 代码审查 Skill 定义 |
| `code_review/config.yaml` | `resources/skills/` | 代码审查配置 |
| `generate_docs/SKILL.md` | `resources/skills/` | 文档生成 Skill 定义 |
| `generate_docs/config.yaml` | `resources/skills/` | 文档生成配置 |
| `analyze_logs/SKILL.md` | `resources/skills/` | 日志分析 Skill 定义 |
| `analyze_logs/config.yaml` | `resources/skills/` | 日志分析配置 |

### 5.2 修改文件

| 文件 | 修改内容 |
|------|----------|
| `Skill.java` | 添加 `getTriggerKeywords()` 默认方法 |
| `AgentLoop.java` | 添加 SkillRouter 硬路由逻辑 |
| `AppBinder.java` | 使用 SkillLoader 加载 Skill |
| `SkillRegistryImpl.java` | 注册 SkillDefinition 而非 Skill |
| `LogParserTool.java` | 保留作为底层 Tool，Skill 内部可调用 |

### 5.3 Skill 实现类改造

| 文件 | 改造内容 |
|------|----------|
| `CodeReviewSkill.java` | 实现 `SkillExecutor`，移除内部工具调用逻辑 |
| `DocGenSkill.java` | 实现 `SkillExecutor` |
| `LogAnalysisSkill.java` | 实现 `SkillExecutor` |

---

## 6. 执行流程

### 6.1 启动流程

```
AppBinder.start()
    │
    ▼
SkillLoader.loadAll("src/main/resources/skills/")
    │
    ├─ 解析 code_review/SKILL.md
    ├─ 解析 code_review/config.yaml
    └─ 合并 → SkillDefinition
    │
    ▼
SkillRegistry.register(skillDefinitions)
    │
    ▼
AgentLoop 初始化（持有 SkillRouter）
```

### 6.2 用户请求流程

```
用户: "帮我审查一下代码"
    │
    ▼
AgentLoop.runStream()
    │
    ▼
SkillRouter.route("帮我审查一下代码")
    │
    ├─ 检查触发关键词: "审查" 命中 code_review
    ├─ 检查禁用关键词: 无
    └─ 返回 SkillRoute(code_review, TRIGGER_KEYWORD)
    │
    ▼
强制执行 code_review Skill
    │
    ▼
SkillExecutor.execute(context)
    │
    ├─ 调用 execute_command: git diff --cached
    ├─ 调用 read_file: 读取变更文件
    └─ 返回审查结果
    │
    ▼
结果返回给用户
```

---

## 7. 渐进式加载实现

### 7.1 三阶段加载

```java
/**
 * 获取 Skill 简介（用于路由决策）
 */
public String getSkillSummary(SkillDefinition skill) {
    return skill.getName() + ": " + skill.getDescription();
}

/**
 * 获取 Skill 完整内容（用于执行）
 */
public String getSkillFullContent(SkillDefinition skill) {
    return skill.getMetadata().fullContent();
}

/**
 * 获取 refs/ 目录内容（用于 RAG，按需）
 */
public List<Path> getSkillReferences(SkillDefinition skill, String query) {
    // 简单的关键词匹配 or 未来接入 RAG
    Path refsDir = skillDir.resolve("refs");
    if (!Files.exists(refsDir)) return List.of();

    return Files.list(refsDir)
        .filter(f -> matchesQuery(query, f))
        .toList();
}
```

### 7.2 Token 优化

| 阶段 | 内容 | Token 估算 |
|------|------|------------|
| 阶段1（启动） | 所有 Skill 名称+简介 | ~50-100 token/Skill |
| 阶段2（命中） | 命中 Skill 完整 SKILL.md | ~500-1000 token |
| 阶段3（按需） | refs/ 目录文件 | 按需加载 |

相比全部加载（假设 10 个 Skill × 1000 token = 10,000 token），节省约 90%。

---

## 8. 错误处理

| 场景 | 处理方式 |
|------|----------|
| SKILL.md 解析失败 | 记录 warn，启动失败 |
| config.yaml 缺失 | 使用默认配置，允许所有 Tool |
| 禁用关键词命中 | 跳过该 Skill，继续匹配下一个 |
| 触发关键词冲突 | 按注册顺序优先匹配第一个 |
| SkillExecutor 执行异常 | 返回错误结果，不影响其他 Skill |
| Tool 调用权限不足 | 通过 PermissionGate 询问用户 |

---

## 9. 未来扩展（TODO）

| 功能 | 描述 | 优先级 |
|------|------|--------|
| MCP 协议集成 | Tool 通过 MCP 协议统一接入 | P1 |
| RAG 增强 | refs/ 目录支持向量检索 | P2 |
| Skill 版本管理 | 支持多版本 Skill 热切换 | P3 |
| 动态 Skill 加载 | 运行时从远程加载 Skill | P4 |

---

## 10. 验收标准

| 标准 | 验证方式 |
|------|----------|
| 用户说"审查代码"→ 触发 code_review | 集成测试 |
| 用户说"分析日志"→ 触发 analyze_logs | 集成测试 |
| SKILL.md 修改后无需重新编译 | 验证加载逻辑 |
| 禁用关键词命中时 Skill 不触发 | 单元测试 |
| Token 消耗比原来降低 | 日志对比 |
| 现有 Tool 调用不受影响 | 回归测试 |

---

*文档版本：v1.0*
*创建日期：2026-06-04*
