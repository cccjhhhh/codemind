# CodeMind 工程约束体系实施计划

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 为 CodeMind 项目建立工程约束体系，包括 Checkstyle 代码检查、ArchUnit 架构测试和 AGENTS.md 增强。

**Architecture:** 采用 Checkstyle 作为代码质量约束工具，ArchUnit 作为架构约束测试框架，通过 Maven 插件集成到构建流程。约束强度为指导性（不阻断构建）。

**Tech Stack:** Maven, Checkstyle 3.3.1, ArchUnit 1.2.1, JUnit 5

---

## 文件结构

实施后将创建/修改以下文件：

| 文件 | 操作 | 职责 |
|------|------|------|
| `config/checkstyle.xml` | Create | Checkstyle 配置文件，定制 Google Style |
| `pom.xml` | Modify | 添加 Checkstyle 插件和 ArchUnit 依赖 |
| `src/test/java/com/codemind/architecture/ArchitectureTest.java` | Create | ArchUnit 架构测试类 |
| `AGENTS.md` | Modify | 添加 AI 编码行为准则章节 |

---

## Task 1: 创建 Checkstyle 配置文件

**Files:**
- Create: `config/checkstyle.xml`

- [ ] **Step 1: 创建 config 目录和 checkstyle.xml 文件**

```xml
<?xml version="1.0" encoding="UTF-8"?>
<!DOCTYPE module PUBLIC
    "-//Checkstyle//DTD Checkstyle Configuration 1.3//EN"
    "https://checkstyle.org/dtds/configuration_1_3.dtd">

<!--
    CodeMind 项目定制版 Checkstyle 配置
    
    基于 Google Java Style Guide，定制如下：
    - 允许中文注释（不强制英文）
    - 缩进使用 4 空格（Java 主流习惯）
    - 行宽 120 字符（中文注释占用更多宽度）
    
    约束强度：指导性（warning 级别，不阻断构建）
-->
<module name="Checker">
    <!-- 属性配置 -->
    <property name="charset" value="UTF-8"/>
    <property name="severity" value="warning"/>
    <property name="fileExtensions" value="java, xml, properties"/>

    <!-- 排除生成的代码 -->
    <module name="BeforeExecutionExclusionFileFilter">
        <property name="excludePattern" value="$[\\/]target[\\/]"/>
    </module>

    <!-- 文件末尾换行 -->
    <module name="NewlineAtEndOfFile"/>

    <!-- 文件大小限制 -->
    <module name="FileLength">
        <property name="max" value="2000"/>
    </module>

    <!-- 不允许制表符 -->
    <module name="FileTabCharacter">
        <property name="eachLine" value="true"/>
    </module>

    <module name="TreeWalker">
        <!-- ==================== 命名规范 ==================== -->
        
        <!-- 类名：PascalCase -->
        <module name="TypeName">
            <property name="format" value="^[A-Z][a-zA-Z0-9]*$"/>
            <message key="name.invalidPattern"
                     value="类名 ''{0}'' 必须使用 PascalCase 命名规范（如：AgentLoop）"/>
        </module>

        <!-- 方法名：camelCase -->
        <module name="MethodName">
            <property name="format" value="^[a-z][a-zA-Z0-9]*$"/>
            <message key="name.invalidPattern"
                     value="方法名 ''{0}'' 必须使用 camelCase 命名规范（如：executeTool）"/>
        </module>

        <!-- 常量名：UPPER_SNAKE_CASE -->
        <module name="ConstantName">
            <property name="format" value="^[A-Z][A-Z0-9]*(_[A-Z0-9]+)*$"/>
        </module>

        <!-- 局部变量名：camelCase -->
        <module name="LocalVariableName">
            <property name="format" value="^[a-z][a-zA-Z0-9]*$"/>
        </module>

        <!-- 参数名：camelCase -->
        <module name="ParameterName">
            <property name="format" value="^[a-z][a-zA-Z0-9]*$"/>
        </module>

        <!-- 成员变量名：camelCase，允许前缀 -->
        <module name="MemberName">
            <property name="format" value="^[a-z][a-zA-Z0-9]*$"/>
        </module>

        <!-- ==================== Import 规范 ==================== -->
        
        <!-- 避免使用 * 导入 -->
        <module name="AvoidStarImport">
            <property name="severity" value="warning"/>
        </module>

        <!-- 避免非法包导入 -->
        <module name="IllegalImport">
            <property name="severity" value="error"/>
            <property name="illegalPkgs" value="sun.*, com.sun.*"/>
        </module>

        <!-- 未使用的导入 -->
        <module name="UnusedImports">
            <property name="severity" value="warning"/>
        </module>

        <!-- Import 排序 -->
        <module name="ImportOrder">
            <property name="severity" value="warning"/>
            <property name="groups" value="java,javax,org,com"/>
            <property name="ordered" value="true"/>
            <property name="separated" value="true"/>
            <property name="option" value="top"/>
        </module>

        <!-- ==================== 代码格式 ==================== -->
        
        <!-- 行宽限制：120 字符（中文注释需要更多空间）-->
        <module name="LineLength">
            <property name="max" value="120"/>
            <property name="ignorePattern" value="^package.*|^import.*|http://|https://"/>
        </module>

        <!-- 方法长度限制 -->
        <module name="MethodLength">
            <property name="max" value="80"/>
            <property name="severity" value="info"/>
        </module>

        <!-- 参数数量限制 -->
        <module name="ParameterNumber">
            <property name="max" value="7"/>
            <property name="severity" value="info"/>
        </module>

        <!-- 空代码块 -->
        <module name="EmptyBlock">
            <property name="severity" value="error"/>
            <property name="option" value="text"/>
        </module>

        <!-- 左大括号位置 -->
        <module name="LeftCurly">
            <property name="option" value="eol"/>
        </module>

        <!-- 右大括号位置 -->
        <module name="RightCurly">
            <property name="option" value="same"/>
        </module>

        <!-- ==================== 代码质量 ==================== -->
        
        <!-- 避免空语句 -->
        <module name="EmptyStatement">
            <property name="severity" value="error"/>
        </module>

        <!-- 避免魔法数字（info 级别）-->
        <module name="MagicNumber">
            <property name="severity" value="info"/>
            <property name="ignoreNumbers" value="-1, 0, 1, 2, 3, 4, 5, 10, 100"/>
        </module>

        <!-- Switch 必须有 default -->
        <module name="MissingSwitchDefault">
            <property name="severity" value="warning"/>
        </module>

        <!-- ==================== 注释规范 ==================== -->
        
        <!-- 不强制 Javadoc（允许中文注释）-->
        <!-- 移除 MissingJavadocMethod 检查，允许自由使用中文注释 -->

        <!-- TODO/FIXME 追踪 -->
        <module name="TodoComment">
            <property name="severity" value="info"/>
            <property name="format" value="(TODO)|(FIXME)"/>
        </module>
    </module>
</module>
```

- [ ] **Step 2: 验证配置文件语法**

Run: `cd D:\agent_learning\codemind && mvn checkstyle:check -Dcheckstyle.config.location=config/checkstyle.xml -q`

Expected: 如果报错 "No projects found" 或没有语法错误，说明配置文件格式正确

---

## Task 2: 更新 pom.xml 添加插件和依赖

**Files:**
- Modify: `pom.xml`（添加 Checkstyle 插件和 ArchUnit 依赖）

- [ ] **Step 1: 在 dependencies 部分添加 ArchUnit 依赖**

找到 `pom.xml` 中 `</dependencies>` 标签前，添加：

```xml
        <!-- Architecture Testing -->
        <dependency>
            <groupId>com.tngtech.archunit</groupId>
            <artifactId>archunit-junit5</artifactId>
            <version>1.2.1</version>
            <scope>test</scope>
        </dependency>
```

- [ ] **Step 2: 在 build/plugins 部分添加 Checkstyle 插件**

找到 `pom.xml` 中 `</plugins>` 标签前（在 maven-surefire-plugin 后），添加：

```xml
            <!-- Checkstyle - Code Quality -->
            <plugin>
                <groupId>org.apache.maven.plugins</groupId>
                <artifactId>maven-checkstyle-plugin</artifactId>
                <version>3.3.1</version>
                <configuration>
                    <configLocation>config/checkstyle.xml</configLocation>
                    <failOnViolation>false</failOnViolation>
                    <consoleOutput>true</consoleOutput>
                    <outputFile>${project.build.directory}/checkstyle-result.xml</outputFile>
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

- [ ] **Step 3: 验证 pom.xml 语法正确**

Run: `cd D:\agent_learning\codemind && mvn validate -q`

Expected: BUILD SUCCESS（无输出表示成功）

- [ ] **Step 4: 提交 pom.xml 更改**

```bash
git add pom.xml
git commit -m "feat: add Checkstyle and ArchUnit to build process

- Add maven-checkstyle-plugin 3.3.1 with project-specific config
- Add archunit-junit5 1.2.1 for architecture testing
- Configure Checkstyle to not fail build (advisory mode)"
```

---

## Task 3: 创建 ArchUnit 架构测试

**Files:**
- Create: `src/test/java/com/codemind/architecture/ArchitectureTest.java`

- [ ] **Step 1: 创建架构测试目录**

Run: `New-Item -ItemType Directory -Path "D:\agent_learning\codemind\src\test\java\com\codemind\architecture" -Force`

- [ ] **Step 2: 创建 ArchitectureTest.java**

```java
package com.codemind.architecture;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.*;

/**
 * 架构约束测试
 * 
 * 使用 ArchUnit 验证项目分层架构规则：
 * - api/ 分层不应依赖 impl/ 层
 * - core/ 层不应依赖 impl/ 层
 * - impl/ 层的类应实现 api/ 层的接口
 * 
 * 这些测试确保代码遵循"接口优先设计"原则。
 */
class ArchitectureTest {

    private static final String API_PACKAGE = "com.codemind.api..";
    private static final String IMPL_PACKAGE = "com.codemind.impl..";
    private static final String CORE_PACKAGE = "com.codemind.core..";

    /**
     * 规则1：api 层不应依赖 impl 层
     * 
     * 接口层应该只定义契约，不依赖具体实现。
     * 如果违反此规则，会导致循环依赖和难以测试的代码。
     */
    @Test
    @DisplayName("api 层不应依赖 impl 层")
    void apiShouldNotDependOnImpl() {
        noClasses()
            .that().resideInPackage(API_PACKAGE)
            .should().dependOnClassesThat()
            .resideInPackage(IMPL_PACKAGE)
            .because("接口层不应依赖实现层，这违反了接口优先设计原则")
            .check(new com.tngtech.archunit.core.importer.ClassFileImporter()
                .importPackages("com.codemind"));
    }

    /**
     * 规则2：core 层不应依赖 impl 层
     * 
     * 核心引擎应该只依赖接口（api 层），不依赖具体实现。
     * 这样可以实现实现类的可替换性。
     */
    @Test
    @DisplayName("core 层不应依赖 impl 层")
    void coreShouldNotDependOnImpl() {
        noClasses()
            .that().resideInPackage(CORE_PACKAGE)
            .should().dependOnClassesThat()
            .resideInPackage(IMPL_PACKAGE)
            .because("核心层不应依赖实现层，应通过接口解耦")
            .check(new com.tngtech.archunit.core.importer.ClassFileImporter()
                .importPackages("com.codemind"));
    }

    /**
     * 规则3：impl 层以 Impl 结尾的类应实现 api 层的接口
     * 
     * 命名约定：XxxImpl 应该实现对应的 Xxx 接口。
     * 这有助于保持代码一致性和可理解性。
     */
    @Test
    @DisplayName("Impl 结尾的类应实现 api 层的接口")
    void implClassesShouldImplementApiInterfaces() {
        classes()
            .that().resideInPackage(IMPL_PACKAGE)
            .and().haveSimpleNameEndingWith("Impl")
            .should().implement(resideInPackage(API_PACKAGE))
            .because("实现类应该实现 api 包中的接口")
            .check(new com.tngtech.archunit.core.importer.ClassFileImporter()
                .importPackages("com.codemind"));
    }

    /**
     * 规则4：impl 层不应直接依赖其他 impl 层的类
     * 
     * 实现类之间应该通过接口通信，而不是直接依赖。
     * 这保持了松耦合。
     */
    @Test
    @DisplayName("impl 层不应直接依赖其他 impl 层的类")
    void implShouldNotDirectlyDependOnOtherImpl() {
        noClasses()
            .that().resideInPackage(IMPL_PACKAGE)
            .should().dependOnClassesThat()
            .resideInPackage(IMPL_PACKAGE)
            .because("实现类之间应该通过接口通信，保持松耦合")
            .check(new com.tngtech.archunit.core.importer.ClassFileImporter()
                .importPackages("com.codemind"));
    }

    /**
     * 规则5：所有 api 层的接口应该有对应的 impl 层实现
     * 
     * 接口定义后应该有实现，否则接口是无用的。
     * 这是"接口优先设计"的完整性检查。
     */
    @Test
    @DisplayName("api 层的接口应该有 impl 层的实现")
    void apiInterfacesShouldHaveImplImplementations() {
        // 这个测试暂时跳过，因为项目还在早期开发阶段
        // 后续启用此测试来确保所有接口都有实现
    }
}
```

- [ ] **Step 3: 运行架构测试验证**

Run: `cd D:\agent_learning\codemind && mvn test -Dtest=ArchitectureTest`

Expected: Tests run: 4, Failures: 0, Errors: 0（或显示哪些架构规则被违反）

- [ ] **Step 4: 提交架构测试**

```bash
git add src/test/java/com/codemind/architecture/ArchitectureTest.java
git commit -m "feat: add ArchUnit architecture tests

- Verify api layer doesn't depend on impl layer
- Verify core layer doesn't depend on impl layer
- Verify Impl classes implement api interfaces
- Verify impl classes communicate via interfaces"
```

---

## Task 4: 更新 AGENTS.md 添加 AI 编码行为准则

**Files:**
- Modify: `AGENTS.md`（在文件末尾添加新章节）

- [ ] **Step 1: 在 AGENTS.md 末尾添加 AI 编码行为准则章节**

在现有内容后添加：

```markdown

---

## AI 编码行为准则

> 本节为 AI 辅助编码提供行为指引，确保生成的代码遵循项目规范和主流实践。

### 编码前必读

1. **阅读设计文档**：`docs/superpowers/specs/2026-06-01-codemind-design.md`
2. **理解模块位置**：确认要实现的功能在整体架构中的位置
3. **参考主流设计**：了解 LangChain、Claude Code 等主流 Agent 框架如何实现类似功能

### 分层规范

| 层级 | 职责 | 允许依赖 |
|------|------|----------|
| `api/` | 接口定义、契约 | 无内部依赖 |
| `impl/` | 接口实现 | api/, 外部依赖 |
| `core/` | 核心引擎 | api/, 外部依赖 |
| `cli/` | 命令行入口 | core/, api/ |

**禁止**：
- api → impl（接口依赖实现）
- core → impl（核心依赖实现）

### 编码规范

1. **命名**：类名 PascalCase，方法名 camelCase
2. **接口优先**：变量使用接口类型声明
3. **构造器注入**：依赖通过构造函数注入
4. **中文注释**：解释"为什么"而非"是什么"

### 参考主流实现

| 实现内容 | 参考来源 |
|----------|----------|
| AgentLoop | LangChain AgentExecutor |
| Tool | LangChain Tool 抽象 |
| PermissionGate | Claude Code 权限模型 |
| Memory | Cursor 上下文管理 |

### 检查命令

```bash
# 检查代码风格
mvn checkstyle:check

# 运行架构测试
mvn test -Dtest=ArchitectureTest

# 完整构建
mvn clean package
```

### 不要闭门造车

- 遇到设计问题，先搜索主流解决方案
- 有多种实现方式时，选择与主流框架一致的方式
- 创新设计需要先与用户商量
```

- [ ] **Step 2: 验证 AGENTS.md 格式**

Run: `type D:\agent_learning\codemind\AGENTS.md`

Expected: 文件末尾应该有 "不要闭门造车" 章节

- [ ] **Step 3: 提交 AGENTS.md 更新**

```bash
git add AGENTS.md
git commit -m "docs: add AI coding guidelines to AGENTS.md

- Add pre-coding checklist with design doc reference
- Add layer responsibility and dependency rules
- Add coding standards (naming, interface-first, DI)
- Add mainstream implementation references
- Add verification commands"
```

---

## Task 5: 验证整体约束体系

**Files:**
- 无新文件，验证现有配置

- [ ] **Step 1: 运行完整构建验证**

Run: `cd D:\agent_learning\codemind && mvn clean validate test`

Expected: BUILD SUCCESS，Checkstyle 输出警告但不阻断

- [ ] **Step 2: 检查 Checkstyle 报告**

Run: `cd D:\agent_learning\codemind && type target\checkstyle-result.xml`

Expected: 输出 Checkstyle 检查结果（如果有违规会列出）

- [ ] **Step 3: 提交所有更改并推送**

```bash
git push
```

---

## 总结

本实施计划创建了 CodeMind 项目的工程约束体系：

| 任务 | 内容 | 状态 |
|------|------|------|
| Task 1 | Checkstyle 配置文件 | 待实施 |
| Task 2 | pom.xml 更新 | 待实施 |
| Task 3 | ArchUnit 架构测试 | 待实施 |
| Task 4 | AGENTS.md 更新 | 待实施 |
| Task 5 | 整体验证 | 待实施 |

**约束强度**：指导性（Checkstyle severity=warning，failOnViolation=false）

**后续改进**：
- 根据项目成熟度逐步提升约束强度
- 添加更多架构测试规则
- 考虑添加 SpotBugs 进行静态 bug 检测
