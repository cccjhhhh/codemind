# CodeMind - 工程约束体系设计文档

> 为 CodeMind 项目建立类似 Harness Engineering 的工程约束，指导 AI 编码行为，确保代码质量和架构一致性。

---

## 一、背景与目标

### 1.1 问题背景

当前 CodeMind 项目在 AI 辅助编码时存在以下问题：
- **代码质量问题**：AI 生成的代码风格不一致、命名混乱
- **架构偏离问题**：AI 没有严格遵循 api/impl 分层设计
- **实现质量问题**：AI 写的代码有 bug、未处理边界情况
- **工作流程问题**：AI 编码时缺乏明确的参考和约束

### 1.2 设计目标

建立一套**指导性（轻量）**的工程约束体系：
1. 约束 AI 编码行为，参考最新主流 Agent 开发实践
2. 使用工具（Checkstyle + ArchUnit）作为主要约束形式
3. 覆盖代码质量和架构约束两个层面
4. 保持学习项目的灵活性，不强制阻断构建

### 1.3 核心原则

> **主流优先，自主为例外**
>
> 本项目是学习 Agent 开发的项目，AI 编码时应参考当今最新最主流的 Agent 开发实践。

---

## 二、整体架构

```
┌─────────────────────────────────────────────────────────────────┐
│                    CodeMind 工程约束体系                          │
├─────────────────────────────────────────────────────────────────┤
│                                                                 │
│  ┌─────────────────────────────────────────────────────────┐   │
│  │                    AI 编码指导层                          │   │
│  │    AGENTS.md (增强版) + 主流实践参考                      │   │
│  │    • 告诉 AI 遵循哪些主流设计                              │   │
│  │    • 引导 AI 先检查现有代码风格                            │   │
│  │    • 指明分层边界和依赖方向                                │   │
│  │    • 参考主流 Agent 框架实现                               │   │
│  └─────────────────────────────────────────────────────────┘   │
│                                                                 │
│  ┌─────────────────────────────────────────────────────────┐   │
│  │                    代码质量约束层                          │   │
│  │    Checkstyle (Google Java Style)                        │   │
│  │    • 命名规范检查                                          │   │
│  │    • 代码格式检查                                          │   │
│  │    • 注释规范检查（中文注释）                              │   │
│  └─────────────────────────────────────────────────────────┘   │
│                                                                 │
│  ┌─────────────────────────────────────────────────────────┐   │
│  │                    架构约束层                              │   │
│  │    ArchUnit 测试                                          │   │
│  │    • api/impl 分层强制                                     │   │
│  │    • 依赖方向检查（impl → api, 不能反向）                  │   │
│  │    • 接口优先设计验证                                      │   │
│  └─────────────────────────────────────────────────────────┘   │
│                                                                 │
│  ┌─────────────────────────────────────────────────────────┐   │
│  │                    Maven 集成层                            │   │
│  │    pom.xml 插件配置                                        │   │
│  │    • maven-checkstyle-plugin                              │   │
│  │    • maven-surefire-plugin (运行 ArchUnit 测试)           │   │
│  └─────────────────────────────────────────────────────────┘   │
│                                                                 │
└─────────────────────────────────────────────────────────────────┘
```

---

## 三、主流 Agent 开发实践参考

### 3.1 需要参考的主流框架/项目

```
AI 编码时应参考的 Agent 最佳实践来源：
├── 框架层
│   ├── LangChain (Python) - 工具链编排模式
│   ├── AutoGPT - 自主任务分解模式
│   └── Semantic Kernel - 技能组合模式
│
├── 产品层
│   ├── Claude Code - 工具使用安全模式
│   ├── Cursor - 上下文管理策略
│   └── Aider - 增量修改模式
│
└── 设计模式
    ├── ReAct (Reasoning + Acting)
    ├── Function Calling / Tool Use
    └── Chain-of-Thought Prompting
```

### 3.2 CodeMind 模块与主流设计对照

| CodeMind 模块 | 应参考的主流设计 | 来源 |
|--------------|-----------------|------|
| **AgentLoop** | ReAct 循环模式 | LangChain AgentExecutor |
| **Tool/ToolRegistry** | Function Calling 协议 | OpenAI Tool Use |
| **PermissionGate** | 安全确认机制 | Claude Code 权限模型 |
| **Session/Memory** | 上下文窗口管理 | Cursor 记忆策略 |
| **Skill** | 技能组合与编排 | LangChain Chain / Semantic Kernel Skill |

### 3.3 参考原则

| 原则 | 说明 |
|------|------|
| **参考主流** | 实现新功能前，先研究业界主流方案 |
| **复用成熟设计** | 优先采用社区验证过的架构和模式 |
| **自主设计需商量** | 如需跳出主流方案自主设计，必须先与用户商量 |
| **保持一致性** | 项目内部保持设计一致性 |

---

## 四、代码质量约束

### 4.1 规范来源

采用 **Google Java Style Guide** 作为基础：
- 业界最广泛使用的 Java 编码规范
- Checkstyle 官方直接支持
- 学习项目应该学习主流标准

### 4.2 项目定制规则

基于项目特点，在 Google Style 基础上定制：

| 规则类别 | Google 默认 | 项目定制 | 理由 |
|----------|-------------|---------|------|
| **注释语言** | 英文 | 允许中文 | 项目用于学习，中文注释更友好 |
| **缩进** | 2空格 | 4空格 | Java 主流习惯 |
| **行宽** | 100字符 | 120字符 | 中文注释占用更多宽度 |
| **import排序** | 推荐 | 强制 | 保持代码整洁 |

### 4.3 检查规则分类

```
Checkstyle 规则优先级：
├── Errors（必须通过）
│   ├── 命名规范（类名 PascalCase，方法名 camelCase）
│   ├── 非法import（如 sun.* 包）
│   └── 空代码块
│
├── Warnings（建议修复）
│   ├── 代码格式（缩进、空格）
│   ├── 魔法数字
│   └── 方法长度
│
└── Info（仅供参考）
    ├── TODO/FIXME 数量
    └── 注释覆盖率
```

### 4.4 Maven 集成

```xml
<!-- 在 pom.xml 中添加 -->
<plugin>
    <groupId>org.apache.maven.plugins</groupId>
    <artifactId>maven-checkstyle-plugin</artifactId>
    <version>3.3.1</version>
    <configuration>
        <configLocation>config/checkstyle.xml</configLocation>
        <failOnViolation>false</failOnViolation> <!-- 不阻断构建 -->
        <consoleOutput>true</consoleOutput>
    </configuration>
    <executions>
        <execution>
            <phase>validate</phase>
            <goals>
                <goal>check</goal>
            </goals>
        </execution>
    </executions>
</plugin>
```

---

## 五、架构约束

### 5.1 分层规则

```
允许的包依赖方向：
┌─────────────────────────────────────────────────────────────┐
│                                                             │
│    cli/  ──────────────▶  core/                           │
│                              │                              │
│                              ▼                              │
│    impl/  ◀─────────────── api/                            │
│                              │                              │
│                              ▼                              │
│    bootstrap/  ────────────▶  impl/                        │
│                              │                              │
│                              ▼                              │
│    dto/  ─────────────────▶  api/  (仅数据，无业务)       │
│                                                             │
└─────────────────────────────────────────────────────────────┘

禁止的依赖方向：
✗ impl → core      (实现层不能依赖核心引擎)
✗ api → impl       (接口层不能依赖实现层)
✗ core → impl      (核心层不能依赖实现层)
✗ dto → impl       (DTO 不应依赖实现层)
```

### 5.2 ArchUnit 架构测试

```java
package com.codemind.architecture;

import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.lang.ArchRule;
import com.tngtech.archunit.junit5.ArchTest;
import com.tngtech.archunit.junit5.ArchUnitSupport;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.*;

@ArchUnitSupport
class ArchitectureTest {
    
    // 分层规则：core 不能依赖 impl
    @ArchTest
    public ArchRule coreShouldNotDependOnImpl = noClasses()
        .that().resideInPackage("com.codemind.core..")
        .should().dependOnClassesThat()
        .resideInPackage("com.codemind.impl..")
        .because("核心层不应依赖实现层");
    
    // 分层规则：api 不能依赖 impl
    @ArchTest
    public ArchRule apiShouldNotDependOnImpl = noClasses()
        .that().resideInPackage("com.codemind.api..")
        .should().dependOnClassesThat()
        .resideInPackage("com.codemind.impl..")
        .because("接口层不应依赖实现层");
    
    // 分层规则：dto 不能依赖 impl
    @ArchTest
    public ArchRule dtoShouldNotDependOnImpl = noClasses()
        .that().resideInPackage("com.codemind.dto..")
        .should().dependOnClassesThat()
        .resideInPackage("com.codemind.impl..")
        .because("DTO 层不应依赖实现层");
    
    // 接口优先：impl 包中的类应该实现 api 包中的接口
    @ArchTest
    public ArchRule implClassesShouldImplementApiInterfaces = classes()
        .that().resideInPackage("com.codemind.impl..")
        .and().haveSimpleNameEndingWith("Impl")
        .should().implement(anyInterfaceThat()
            .resideInPackage("com.codemind.api.."))
        .orShould().haveSimpleNameNotEndingWith("Impl")
        .because("实现类应该实现 api 包中的接口");
}
```

### 5.3 Maven 集成

```xml
<!-- 添加 ArchUnit 依赖 -->
<dependency>
    <groupId>com.tngtech.archunit</groupId>
    <artifactId>archunit-junit5</artifactId>
    <version>1.2.1</version>
    <scope>test</scope>
</dependency>
```

---

## 六、AI 编码行为约束

### 6.1 AGENTS.md 增强内容

在现有 AGENTS.md 基础上，增加以下章节：

```markdown
## AI 编码行为准则

### 编码前必读
1. 阅读设计文档 `docs/superpowers/specs/2026-06-01-codemind-design.md`
2. 理解当前模块在整体架构中的位置
3. 确认要实现的功能在主流 Agent 框架中是如何设计的

### 编码前
1. 先阅读 `src/main/java/com/codemind/` 下相关模块的现有代码
2. 遵循现有代码的风格和模式
3. 确认要修改的文件属于哪个层（api/impl/core/cli）
4. 检查现有方法引用：避免创建与现有方法功能重复的新方法

### 编码时
1. 新类放在正确层：
   - 接口 → `api/` 包
   - 实现 → `impl/` 包
   - DTO → `dto/` 包
2. 优先使用接口类型声明变量
3. 依赖通过构造器注入
4. 使用中文注释解释"为什么"，而非"是什么"
5. 新增方法时确保会被调用，否则添加保留理由注释

### 参考主流实现
- 实现 AgentLoop → 参考 LangChain AgentExecutor 源码
- 实现 Tool → 参考 LangChain Tool 抽象
- 实现权限控制 → 参考 Claude Code 的 Permission 机制
- 实现记忆 → 参考 Cursor 的上下文管理

### 编码后
1. 运行 `mvn checkstyle:check` 检查代码风格
2. 运行 `mvn test` 确保架构测试通过
3. 检查方法引用：确认新增方法会被调用，未调用方法需添加保留理由注释
4. 如有违规，优先按提示修复

### 不要闭门造车
- 遇到设计问题，先搜索主流解决方案
- 有多种实现方式时，选择与主流框架一致的方式
- 创新设计需要先与用户商量
```

### 6.2 约束优先级

```
┌─────────────────────────────────────────────────────────────┐
│                      约束强度分层                              │
├─────────────────────────────────────────────────────────────┤
│                                                             │
│  🔴 硬约束（AI 必须遵守）                                     │
│     • 分层边界（api/impl/core/cli/dto/bootstrap）            │
│     • 依赖方向（impl → api，不能反向）                        │
│     • 接口优先（变量用接口类型声明）                          │
│     • 单一职责（DTO 必须独立文件，不在实现类内定义）          │
│     • 方法引用检查（未引用方法需阐述保留理由）⭐ 新增          │
│     • 参考主流设计（不闭门造车）                              │
│                                                             │
│  🟡 软约束（AI 应该遵守，但不强制）                           │
│     • 代码格式（缩进、空格）                                  │
│     • 命名规范（Google Style）                               │
│     • 中文注释                                               │
│                                                             │
│  🟢 建议（AI 可以参考）                                      │
│     • 方法长度控制                                           │
│     • 注释覆盖率                                             │
│     • 避免魔法数字                                           │
│                                                             │
└─────────────────────────────────────────────────────────────┘
```

---

## 七、文件结构

实施后的项目文件结构：

```
codemind/
├── config/
│   └── checkstyle.xml              # Checkstyle 配置文件
├── docs/
│   └── superpowers/specs/
│       ├── 2026-06-01-codemind-design.md        # 原有设计文档
│       └── 2026-06-02-codemind-engineering-constraints.md  # 本文档
├── src/
│   ├── main/
│   │   └── java/com/codemind/
│   │       ├── api/                # 接口层
│   │       ├── dto/                # 数据传输对象层 ⭐ 新增
│   │       ├── impl/               # 实现层
│   │       │   └── bootstrap/      # 依赖注入配置 ⭐ 新增
│   │       ├── core/               # 核心层
│   │       └── cli/                # CLI 层
│   └── test/
│       └── java/com/codemind/
│           └── architecture/       # 架构测试
│               └── ArchitectureTest.java
├── pom.xml                         # 更新：添加 Checkstyle + ArchUnit
└── AGENTS.md                       # 更新：添加 AI 编码行为准则 + 架构约束规范
```

---

## 八、使用流程

### 8.1 日常开发流程

```bash
# 1. 编码前检查现有代码风格
mvn checkstyle:check

# 2. 编码后检查
mvn validate    # 运行 Checkstyle

# 3. 运行测试（包括架构测试）
mvn test

# 4. 完整构建
mvn clean package
```

### 8.2 AI 编码建议

1. **编码前**：先阅读 AGENTS.md 和相关设计文档
2. **编码时**：参考主流 Agent 框架的实现方式
3. **编码后**：运行 `mvn validate test` 检查违规
4. **有违规时**：按提示修复，参考 Google Java Style Guide

---

## 九、总结

本设计文档建立了 CodeMind 项目的工程约束体系：

1. **代码质量约束**：Checkstyle + Google Java Style（定制中文注释支持）
2. **架构约束**：ArchUnit 测试验证分层和依赖方向
3. **AI 行为约束**：AGENTS.md 增强版，包含主流实践参考
4. **约束强度**：指导性（不阻断构建），适合学习项目

核心理念：**主流优先，自主为例外** —— AI 编码时应参考当今最新最主流的 Agent 开发实践。

---

*文档版本：v1.0*
*创建日期：2026-06-02*
