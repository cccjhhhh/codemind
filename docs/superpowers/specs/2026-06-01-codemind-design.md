# CodeMind - 智能Coding助手 设计文档

> 项目定位：一个实用的智能编程助手应用，帮助开发者进行代码审查、代码搜索、文档生成、日志分析等工作。
> 
> 学习目标：通过构建此应用，系统学习 Agent 应用开发的核心概念和最佳实践。

---

## 一、项目概述

### 1.1 为什么做这个项目？

**问题背景**：
- 开发者日常工作中存在大量重复性、模式化的工作
- 代码审查耗时且容易遗漏问题
- 文档编写枯燥且容易过时
- 日志分析需要经验和时间
- 新人上手代码库需要大量时间摸索

**解决方案**：
CodeMind 是一个智能编程助手，通过 Agent 能力自动化处理这些工作，提升开发效率。

### 1.2 项目定位

| 维度 | 说明 |
|------|------|
| **类型** | 实用应用（非框架、非Demo） |
| **语言** | Java（用户主导语言，便于实际使用） |
| **周期** | 预计 1.5-2 个月，每周 15-20 小时 |
| **产出** | 可写简历、可实际使用的产品 |

### 1.3 核心功能

```
┌─────────────────────────────────────────────────────────────┐
│                      CodeMind 功能架构                        │
├─────────────────────────────────────────────────────────────┤
│                                                             │
│  ┌─────────────┐  ┌─────────────┐  ┌─────────────┐        │
│  │  Code Review │  │ 代码搜索     │  │ 文档生成     │        │
│  │  代码审查    │  │ Code Search │  │ Doc Gen     │        │
│  └─────────────┘  └─────────────┘  └─────────────┘        │
│                                                             │
│  ┌─────────────┐  ┌─────────────┐  ┌─────────────┐        │
│  │  日志分析    │  │ 命令执行     │  │ 智能问答     │        │
│  │  Log Analyst│  │ Cmd Runner  │  │ Q&A Chat    │        │
│  └─────────────┘  └─────────────┘  └─────────────┘        │
│                                                             │
└─────────────────────────────────────────────────────────────┘
```

---

## 二、核心概念与学习目标

> 每个模块都对应 Agent 开发的核心概念，边做边学。

### 2.1 Agent 核心概念映射

| 模块 | Agent 概念 | 学习要点 |
|------|-----------|---------|
| **AgentLoop** | Agent 循环 | 理解 Agent 如何"思考-行动-观察"的循环过程 |
| **LLMClient** | LLM 集成 | 学习如何与 LLM API 交互，处理流式响应 |
| **ToolRegistry** | 工具注册 | 理解 Function Calling 机制，工具的定义与注册 |
| **Session** | 会话管理 | 学习多轮对话上下文管理、历史记录存储 |
| **Memory** | 记忆系统 | 理解短期记忆（上下文）vs 长期记忆（向量存储）|
| **PermissionGate** | 安全网关 | 学习 Agent 权限控制、危险操作确认机制 |
| **Skills** | 技能系统 | 理解如何将复杂任务封装为可复用的技能 |
| **SafetyChecker** | 安全检查 | 学习输入验证、输出过滤、防止 Prompt 注入 |

### 2.2 Agent 循环详解

```
┌──────────────────────────────────────────────────────────┐
│                    Agent Loop 核心流程                     │
├──────────────────────────────────────────────────────────┤
│                                                          │
│    ┌─────────┐                                           │
│    │  用户输入 │                                          │
│    └────┬────┘                                           │
│         ▼                                                │
│    ┌─────────┐     ┌─────────────────────────────────┐  │
│    │  思考    │────▶│ LLM 分析输入，决定是否调用工具    │  │
│    └────┬────┘     └─────────────────────────────────┘  │
│         ▼                                                │
│    ┌─────────┐     ┌─────────────────────────────────┐  │
│    │  行动    │────▶│ 执行工具调用（如读取文件、搜索）   │  │
│    └────┬────┘     └─────────────────────────────────┘  │
│         ▼                                                │
│    ┌─────────┐     ┌─────────────────────────────────┐  │
│    │  观察    │────▶│ 收集工具执行结果，作为下一次输入   │  │
│    └────┬────┘     └─────────────────────────────────┘  │
│         │                                                │
│         ▼                                                │
│    ┌─────────┐                                           │
│    │ 继续循环?│─── 是 ──▶ 返回"思考"步骤                  │
│    └────┬────┘                                           │
│         │ 否                                              │
│         ▼                                                │
│    ┌─────────┐                                           │
│    │  输出结果 │                                          │
│    └─────────┘                                           │
│                                                          │
└──────────────────────────────────────────────────────────┘
```

---

## 三、系统架构

### 3.1 整体架构图

```
┌─────────────────────────────────────────────────────────────────┐
│                        CodeMind 系统架构                          │
├─────────────────────────────────────────────────────────────────┤
│                                                                 │
│  ┌─────────────────────────────────────────────────────────┐   │
│  │                    用户界面层 (CLI/GUI)                    │   │
│  │    命令行界面  │  交互式REPL  │  可选：Web界面            │   │
│  └─────────────────────────────┬───────────────────────────┘   │
│                                │                                │
│  ┌─────────────────────────────▼───────────────────────────┐   │
│  │                    会话层 (Session Layer)                 │   │
│  │    SessionManager  │  ContextManager  │  HistoryStore    │   │
│  └─────────────────────────────┬───────────────────────────┘   │
│                                │                                │
│  ┌─────────────────────────────▼───────────────────────────┐   │
│  │                    Agent 核心层 (Core Layer)              │   │
│  │    AgentLoop  │  LLMClient  │  ToolRegistry  │  Memory   │   │
│  └─────────────────────────────┬───────────────────────────┘   │
│                                │                                │
│  ┌─────────────────────────────▼───────────────────────────┐   │
│  │                    工具层 (Tool Layer)                    │   │
│  │  FileReader │ CodeSearch │ GitOps │ CmdRunner │ LogParser│   │
│  └─────────────────────────────┬───────────────────────────┘   │
│                                │                                │
│  ┌─────────────────────────────▼───────────────────────────┐   │
│  │                    安全层 (Safety Layer)                  │   │
│  │    PermissionGate  │  SafetyChecker  │  RateLimiter       │   │
│  └─────────────────────────────────────────────────────────┘   │
│                                                                 │
│  ┌─────────────────────────────────────────────────────────┐   │
│  │                    技能层 (Skills Layer)                  │   │
│  │  CodeReviewSkill │ DocGenSkill │ LogAnalysisSkill │ ...   │   │
│  └─────────────────────────────────────────────────────────┘   │
│                                                                 │
└─────────────────────────────────────────────────────────────────┘
```

### 3.2 模块依赖关系

```
                    ┌──────────────┐
                    │   CLI/GUI    │
                    └──────┬───────┘
                           │
                           ▼
                    ┌──────────────┐
                    │   Session    │
                    └──────┬───────┘
                           │
           ┌───────────────┼───────────────┐
           ▼               ▼               ▼
    ┌────────────┐  ┌────────────┐  ┌────────────┐
    │ AgentLoop  │  │   Memory   │  │   Skills   │
    └─────┬──────┘  └────────────┘  └─────┬──────┘
          │                               │
    ┌─────┴─────┐                   ┌─────┴─────┐
    ▼           ▼                   ▼           ▼
┌────────┐ ┌────────┐          ┌────────┐ ┌────────┐
│LLMClient│ │ToolReg │          │ Tools  │ │Safety │
└────────┘ └────────┘          └────────┘ └────────┘
```

---

## 四、模块详解

### 4.1 核心模块 (core/)

#### 4.1.1 AgentLoop - Agent 循环引擎

**职责**：实现 Agent 的核心"思考-行动-观察"循环

**关键类**：
```java
public class AgentLoop {
    private final LLMClient llmClient;
    private final ToolRegistry toolRegistry;
    private final int maxIterations;
    
    public AgentResult run(UserInput input, SessionContext context) {
        // 1. 构建初始消息
        // 2. 循环：调用 LLM → 解析响应 → 执行工具 → 收集观察
        // 3. 返回最终结果
    }
}
```

**学习要点**：
- ReAct 模式（Reasoning + Acting）
- 循环终止条件设计
- 错误处理与重试机制

#### 4.1.2 LLMClient - LLM 客户端

**职责**：封装与 LLM API 的交互

**关键类**：
```java
public interface LLMClient {
    // 同步调用
    LLMResponse chat(List<Message> messages);
    
    // 流式调用
    void chatStream(List<Message> messages, StreamHandler handler);
    
    // Function Calling
    LLMResponse chatWithTools(List<Message> messages, List<ToolDefinition> tools);
}
```

**学习要点**：
- API 调用封装
- 流式响应处理
- Token 计数与成本控制
- Function Calling 协议

#### 4.1.3 ToolRegistry - 工具注册中心

**职责**：管理所有可用工具的定义和执行

**关键类**：
```java
public class ToolRegistry {
    private final Map<String, Tool> tools = new HashMap<>();
    
    public void register(Tool tool);
    public ToolDefinition getDefinition(String name);
    public ToolResult execute(String name, Map<String, Object> params);
    public List<ToolDefinition> getAllDefinitions();
}

public interface Tool {
    String getName();
    String getDescription();
    JsonNode getInputSchema();      // JSON Schema 格式
    ToolResult execute(Map<String, Object> params);
}
```

**学习要点**：
- 工具的标准化定义
- JSON Schema 参数验证
- 工具执行的超时与错误处理

#### 4.1.4 Memory - 记忆系统

**职责**：管理 Agent 的短期和长期记忆

**架构**：
```
┌─────────────────────────────────────────────────────┐
│                    Memory System                     │
├─────────────────────────────────────────────────────┤
│                                                     │
│  ┌─────────────────────────────────────────────┐   │
│  │         短期记忆 (Short-term Memory)          │   │
│  │    • 当前会话的对话历史                        │   │
│  │    • 上下文窗口管理（滑动窗口/摘要）            │   │
│  │    • 实现：内存中的 List<Message>             │   │
│  └─────────────────────────────────────────────┘   │
│                                                     │
│  ┌─────────────────────────────────────────────┐   │
│  │         长期记忆 (Long-term Memory)           │   │
│  │    • 跨会话的重要信息                          │   │
│  │    • 项目知识库（代码结构、 conventions）       │   │
│  │    • 实现：向量数据库 + Embedding              │   │
│  └─────────────────────────────────────────────┘   │
│                                                     │
└─────────────────────────────────────────────────────┘
```

**学习要点**：
- 上下文窗口限制与策略
- 向量嵌入与相似度搜索
- 记忆的存储与检索

### 4.2 会话模块 (session/)

#### 4.2.1 SessionManager

**职责**：管理用户会话生命周期

```java
public class SessionManager {
    private final SessionStorage storage;
    
    public Session createSession();
    public Session getSession(String sessionId);
    public void saveSession(Session session);
    public void closeSession(String sessionId);
}

public class Session {
    private String id;
    private List<Message> history;
    private SessionContext context;
    private LocalDateTime createdAt;
    private LocalDateTime lastActiveAt;
}
```

#### 4.2.2 ContextManager

**职责**：管理会话上下文（当前工作目录、项目信息等）

```java
public class SessionContext {
    private Path workingDirectory;
    private ProjectInfo projectInfo;
    private Map<String, Object> variables;
}
```

### 4.3 工具模块 (tools/)

每个工具实现 `Tool` 接口：

#### 4.3.1 FileReader - 文件读取工具

```java
public class FileReaderTool implements Tool {
    @Override
    public String getName() { return "read_file"; }
    
    @Override
    public String getDescription() { 
        return "读取指定路径的文件内容"; 
    }
    
    @Override
    public JsonNode getInputSchema() {
        return schemaBuilder()
            .required("path", "string", "文件路径")
            .optional("offset", "integer", "起始行号")
            .optional("limit", "integer", "读取行数")
            .build();
    }
    
    @Override
    public ToolResult execute(Map<String, Object> params) {
        // 实现文件读取逻辑
    }
}
```

#### 4.3.2 CodeSearch - 代码搜索工具

```java
public class CodeSearchTool implements Tool {
    @Override
    public String getName() { return "search_code"; }
    
    // 支持多种搜索模式
    // - 关键字搜索
    // - 正则搜索
    // - AST 模式搜索（可选）
}
```

#### 4.3.3 其他工具

| 工具名 | 功能 | 学习要点 |
|--------|------|----------|
| GitOpsTool | Git 操作（status, diff, log） | 进程执行、命令构建 |
| CmdRunner | 执行 shell 命令 | 安全沙箱、权限控制 |
| LogParser | 解析日志文件 | 正则匹配、结构化提取 |
| WebSearch | 网络搜索 | API 集成、结果解析 |

### 4.4 安全模块 (safety/)

#### 4.4.1 PermissionGate - 权限网关

**职责**：控制危险操作的执行权限

```java
public class PermissionGate {
    public enum Permission {
        READ_FILE,
        WRITE_FILE,
        EXECUTE_COMMAND,
        NETWORK_ACCESS
    }
    
    public boolean checkPermission(Permission perm);
    public boolean requestPermission(Permission perm, String context);
    public void enforcePermission(Permission perm, String context);
}
```

**安全策略**：
```
┌─────────────────────────────────────────────────────┐
│               Permission Decision Flow               │
├─────────────────────────────────────────────────────┤
│                                                     │
│  工具请求执行                                        │
│       │                                             │
│       ▼                                             │
│  ┌─────────────┐    否     ┌─────────────┐         │
│  │ 是否在白名单？│─────────▶│ 拒绝执行     │         │
│  └─────┬───────┘          └─────────────┘         │
│        │ 是                                         │
│        ▼                                             │
│  ┌─────────────┐    否     ┌─────────────┐         │
│  │ 是否低风险？  │─────────▶│ 请求用户确认 │         │
│  └─────┬───────┘          └─────────────┘         │
│        │ 是                                         │
│        ▼                                             │
│  ┌─────────────┐                                    │
│  │ 允许执行     │                                    │
│  └─────────────┘                                    │
│                                                     │
└─────────────────────────────────────────────────────┘
```

#### 4.4.2 SafetyChecker - 安全检查器

**职责**：输入验证、输出过滤

```java
public class SafetyChecker {
    // 检查用户输入是否包含恶意内容
    public boolean isInputSafe(String input);
    
    // 过滤输出中的敏感信息
    public String sanitizeOutput(String output);
    
    // 检测 Prompt 注入
    public boolean detectPromptInjection(String input);
}
```

### 4.5 技能模块 (skills/)

> 技能是对复杂任务流程的封装，可由多个工具调用组合而成。

#### 4.5.1 CodeReviewSkill - 代码审查技能

```java
public class CodeReviewSkill implements Skill {
    @Override
    public String getName() { return "code_review"; }
    
    @Override
    public SkillResult execute(SkillContext context) {
        // 1. 获取待审查的代码变更
        // 2. 分析代码结构
        // 3. 检查编码规范
        // 4. 发现潜在问题
        // 5. 生成审查报告
    }
}
```

**工作流程**：
```
┌──────────────────────────────────────────────────────────┐
│              Code Review Skill Workflow                   │
├──────────────────────────────────────────────────────────┤
│                                                          │
│  1. 获取变更                                              │
│     └─▶ git diff --cached                               │
│                                                          │
│  2. 分析文件                                              │
│     └─▶ 读取变更的文件，理解上下文                         │
│                                                          │
│  3. LLM 审查                                              │
│     └─▶ 调用 LLM 分析代码质量、安全性、可维护性            │
│                                                          │
│  4. 生成报告                                              │
│     └─▶ 格式化输出审查结果                                │
│                                                          │
└──────────────────────────────────────────────────────────┘
```

#### 4.5.2 DocGenSkill - 文档生成技能

```java
public class DocGenSkill implements Skill {
    @Override
    public String getName() { return "generate_docs"; }
    
    @Override
    public SkillResult execute(SkillContext context) {
        // 1. 分析代码结构
        // 2. 提取类、方法、注释信息
        // 3. 生成 Markdown 文档
    }
}
```

#### 4.5.3 LogAnalysisSkill - 日志分析技能

```java
public class LogAnalysisSkill implements Skill {
    @Override
    public String getName() { return "analyze_logs"; }
    
    @Override
    public SkillResult execute(SkillContext context) {
        // 1. 读取日志文件
        // 2. 解析日志格式
        // 3. 识别异常模式
        // 4. 统计分析
        // 5. 生成分析报告
    }
}
```

---

## 五、学习路径与阶段规划

### Phase 1：基础框架搭建（第1-2周）

**目标**：搭建项目骨架，实现最小可用版本

**任务**：
- [ ] 创建项目结构（Maven/Gradle）
- [ ] 实现 LLMClient 基础版本（先支持 OpenAI API）
- [ ] 实现 Tool 接口和 ToolRegistry
- [ ] 实现 AgentLoop 基础循环
- [ ] 实现 CLI 入口

**产出**：能够进行简单对话的命令行工具

**学习重点**：
- Agent 循环的本质
- Function Calling 协议
- API 调用与错误处理

### Phase 2：核心工具开发（第3-4周）

**目标**：开发基础工具集

**任务**：
- [ ] FileReader 工具（读取文件）
- [ ] FileWriter 工具（写入文件，需权限）
- [ ] CodeSearch 工具（代码搜索）
- [ ] CmdRunner 工具（命令执行）
- [ ] 实现 PermissionGate 基础版本

**产出**：能够读取文件、搜索代码、执行命令

**学习重点**：
- 工具设计的最佳实践
- 安全沙箱设计
- 权限控制模型

### Phase 3：会话与记忆（第5-6周）

**目标**：实现会话管理和记忆系统

**任务**：
- [ ] 实现 SessionManager
- [ ] 实现 ContextManager
- [ ] 实现短期记忆（对话历史管理）
- [ ] （可选）实现长期记忆（向量存储）

**产出**：支持多轮对话，记住上下文

**学习重点**：
- 会话生命周期管理
- 上下文窗口策略
- 向量嵌入与检索

### Phase 4：技能系统（第7-8周）

**目标**：实现技能抽象和具体技能

**任务**：
- [ ] 设计 Skill 接口
- [ ] 实现 SkillRegistry
- [ ] 实现 CodeReviewSkill
- [ ] 实现 DocGenSkill
- [ ] 实现 LogAnalysisSkill

**产出**：能够执行复杂的编程任务

**学习重点**：
- 任务分解与编排
- 多工具协作
- 结果格式化

### Phase 5：安全加固与优化（第9-10周）

**目标**：完善安全机制，优化体验

**任务**：
- [ ] 完善 PermissionGate
- [ ] 实现 SafetyChecker
- [ ] 添加 RateLimiter
- [ ] 性能优化
- [ ] 用户体验改进

**产出**：稳定、安全、易用的工具

**学习重点**：
- Agent 安全最佳实践
- 输入验证与输出过滤
- 性能优化策略

---

## 六、后续拓展方向

### 6.1 短期拓展（1-2个月）

#### 6.1.1 多模型支持

**目标**：支持多种 LLM 后端

```
┌─────────────────────────────────────────────────────┐
│                   Multi-Model Support                │
├─────────────────────────────────────────────────────┤
│                                                     │
│  ┌─────────────┐                                   │
│  │  LLMClient  │  (统一接口)                        │
│  └──────┬──────┘                                   │
│         │                                           │
│    ┌────┴────┬─────────────┬─────────────┐         │
│    ▼         ▼             ▼             ▼         │
│ ┌──────┐ ┌──────┐   ┌──────────┐  ┌─────────┐      │
│ │OpenAI│ │Claude│   │Local LLM │  │ Azure   │      │
│ └──────┘ └──────┘   │(Ollama)  │  │ OpenAI  │      │
│                     └──────────┘  └─────────┘      │
│                                                     │
└─────────────────────────────────────────────────────┘
```

**实现要点**：
- 定义统一的 LLMClient 接口
- 实现不同提供商的适配器
- 配置化的模型选择

#### 6.1.2 IDE 插件集成

**目标**：将 CodeMind 集成到 IDE 中

**方案**：
- IntelliJ IDEA 插件（Java 开发者优先）
- VS Code 扩展（后续）

**功能**：
- 右键菜单触发 Code Review
- 快捷键调用常用功能
- 内嵌终端交互

#### 6.1.3 Web 界面

**目标**：提供更友好的交互界面

**技术栈**：
- 后端：Spring Boot（Java）
- 前端：React/Vue

**功能**：
- 可视化对话历史
- 文件浏览器集成
- 设置面板

### 6.2 中期拓展（3-6个月）

#### 6.2.1 多 Agent 协作

**目标**：实现多个 Agent 协同工作

```
┌──────────────────────────────────────────────────────────┐
│                  Multi-Agent Collaboration               │
├──────────────────────────────────────────────────────────┤
│                                                          │
│                      ┌──────────┐                        │
│                      │ Orchestrator│                      │
│                      │   Agent   │                        │
│                      └────┬─────┘                         │
│                           │                               │
│         ┌─────────────────┼─────────────────┐            │
│         ▼                 ▼                 ▼            │
│  ┌────────────┐   ┌────────────┐   ┌────────────┐       │
│  │ Reviewer   │   │ Searcher   │   │ Analyzer   │       │
│  │ Agent      │   │ Agent      │   │ Agent      │       │
│  └────────────┘   └────────────┘   └────────────┘       │
│                                                          │
└──────────────────────────────────────────────────────────┘
```

**学习要点**：
- Agent 角色分工
- 任务分发与结果汇总
- Agent 间通信协议

#### 6.2.2 知识库构建

**目标**：构建项目专属知识库

**实现**：
```
┌─────────────────────────────────────────────────────┐
│                 Knowledge Base Pipeline              │
├─────────────────────────────────────────────────────┤
│                                                     │
│  ┌─────────────┐   ┌─────────────┐   ┌──────────┐ │
│  │ 代码仓库     │──▶│ 文档/注释    │──▶│ Embedding │ │
│  │ Code Repo   │   │ 提取        │   │ 生成      │ │
│  └─────────────┘   └─────────────┘   └─────┬────┘ │
│                                             │       │
│                                             ▼       │
│                                     ┌──────────┐   │
│                                     │ 向量存储  │   │
│                                     │ Vector DB│   │
│                                     └─────┬────┘   │
│                                           │         │
│                                           ▼         │
│  ┌─────────────┐                   ┌──────────┐    │
│  │ 用户查询     │──────────────────▶│ 相似度    │    │
│  │             │                   │ 检索     │    │
│  └─────────────┘                   └──────────┘    │
│                                                     │
└─────────────────────────────────────────────────────┘
```

**学习要点**：
- RAG（检索增强生成）
- 向量数据库选型与使用
- 代码语义理解

#### 6.2.3 自动化测试生成

**目标**：自动为代码生成单元测试

**技能设计**：
```java
public class TestGenSkill implements Skill {
    @Override
    public SkillResult execute(SkillContext context) {
        // 1. 分析源代码
        // 2. 识别测试场景
        // 3. 生成测试用例
        // 4. 执行并验证
    }
}
```

### 6.3 长期拓展（6个月以上）

#### 6.3.1 自我进化机制

**目标**：Agent 能够从反馈中学习和改进

**方向**：
- 用户反馈收集
- 成功/失败案例分析
- Prompt 自动优化
- 工具效果评估

#### 6.3.2 领域定制化

**目标**：支持不同编程语言和领域

**扩展**：
- Python 代码分析支持
- 前端代码审查规则
- 数据库 SQL 优化建议

#### 6.3.3 企业级功能

**目标**：支持企业级部署和使用

**功能**：
- 多租户支持
- 审计日志
- 权限管理
- 私有化部署

---

## 七、技术选型

### 7.1 核心依赖

| 类别 | 技术 | 说明 |
|------|------|------|
| LLM API | OkHttp / Retrofit | HTTP 客户端 |
| JSON 处理 | Jackson / Gson | JSON 序列化 |
| CLI 框架 | Picocli | 命令行解析 |
| 配置管理 | YAML (SnakeYAML) | 配置文件 |
| 日志 | SLF4J + Logback | 日志框架 |
| 测试 | JUnit 5 + Mockito | 单元测试 |
| 向量存储（可选） | Milvus / Chroma | 向量数据库 |

### 7.2 项目结构（企业级分层）

```
codemind/
├── pom.xml                          # Maven 配置
├── README.md
├── docs/
│   └── superpowers/specs/           # 设计文档
├── src/
│   ├── main/
│   │   ├── java/
│   │   │   └── com/
│   │   │       └── codemind/
│   │   │           ├── api/                    # 接口定义层
│   │   │           │   ├── llm/               # LLM 接口
│   │   │           │   │   ├── LLMClient.java
│   │   │           │   │   ├── Message.java
│   │   │           │   │   ├── ToolDefinition.java
│   │   │           │   │   ├── ToolCall.java
│   │   │           │   │   └── LLMResponse.java
│   │   │           │   ├── tool/              # 工具接口
│   │   │           │   │   ├── Tool.java
│   │   │           │   │   └── ToolResult.java
│   │   │           │   ├── skill/             # 技能接口
│   │   │           │   │   ├── Skill.java
│   │   │           │   │   ├── SkillContext.java
│   │   │           │   │   └── SkillResult.java
│   │   │           │   └── session/           # 会话接口
│   │   │           │       ├── SessionManager.java
│   │   │           │       └── SessionContext.java
│   │   │           ├── impl/                   # 实现层
│   │   │           │   ├── llm/               # LLM 实现
│   │   │           │   │   └── OpenAIClient.java
│   │   │           │   ├── tool/              # 工具实现
│   │   │           │   │   ├── FileReaderTool.java
│   │   │           │   │   ├── CodeSearchTool.java
│   │   │           │   │   ├── CommandRunnerTool.java
│   │   │           │   │   └── LogParserTool.java
│   │   │           │   ├── skill/             # 技能实现
│   │   │           │   │   ├── CodeReviewSkill.java
│   │   │           │   │   ├── DocGenSkill.java
│   │   │           │   │   ├── LogAnalysisSkill.java
│   │   │           │   │   └── SkillRegistry.java
│   │   │           │   ├── session/           # 会话实现
│   │   │           │   │   └── SessionManagerImpl.java
│   │   │           │   └── safety/            # 安全实现
│   │   │           │       ├── Permission.java
│   │   │           │       ├── PermissionGate.java
│   │   │           │       └── SafetyChecker.java
│   │   │           ├── core/                   # 核心引擎
│   │   │           │   ├── AgentLoop.java
│   │   │           │   └── AgentResult.java
│   │   │           ├── cli/                    # CLI 模块
│   │   │           │   └── CLI.java
│   │   │           └── CodeMindApplication.java
│   │   └── resources/
│   │       ├── application.yml
│   │       └── prompts/
│   └── test/
│       └── java/                             # 测试代码
└── scripts/
    └── run.sh
```

---

## 八、简历内容建议

### 项目经历模板

```
项目名称：CodeMind - 智能编程助手
项目角色：独立开发者
技术栈：Java, LLM API, Function Calling, Vector DB

项目描述：
基于 LLM 的智能编程助手，提供代码审查、文档生成、日志分析等功能。
实现了完整的 Agent 循环（思考-行动-观察），支持多轮对话和工具调用。

核心工作：
• 设计并实现了 Agent 核心引擎，包括 LLM 客户端、工具注册中心、Agent 循环
• 开发了代码审查、文档生成、日志分析等 6+ 个技能模块
• 实现了基于权限的安全网关，支持危险操作的二次确认
• 设计了记忆系统，支持短期对话历史和长期向量存储

项目成果：
• 支持 Java/Python 代码审查，发现潜在问题的准确率达 85%+
• 日志分析功能帮助快速定位问题，减少排查时间 40%+
• 项目已在 GitHub 获得 xx stars，被 xx 公司采用

学习收获：
• 深入理解 Agent 应用架构和 ReAct 模式
• 掌握 LLM API 集成和 Function Calling 最佳实践
• 学习了向量检索和 RAG 技术的应用
• 积累了 AI 应用安全设计和权限控制经验
```

---

## 九、参考资源

### 9.1 Agent 学习资源

- [Agent-Learning-Hub](https://github.com/datawhalechina/Agent-Learning-Hub) - Agent 学习路线图
- [LangChain Documentation](https://python.langchain.com/docs/) - Agent 框架参考
- [OpenAI Function Calling Guide](https://platform.openai.com/docs/guides/function-calling) - Function Calling 文档

### 9.2 设计参考

- [Claude Code](https://claude.ai/code) - 产品形态参考
- [Cursor](https://cursor.sh/) - IDE 集成参考
- [Aider](https://aider.chat/) - 命令行交互参考

### 9.3 技术文档

- [ReAct: Synergizing Reasoning and Acting in Language Models](https://arxiv.org/abs/2210.03629)
- [Toolformer: Language Models Can Teach Themselves to Use Tools](https://arxiv.org/abs/2302.04041)

---

## 十、总结

CodeMind 是一个实用的智能编程助手项目，通过构建这个应用，你将系统学习 Agent 应用开发的核心概念：

1. **Agent 循环**：理解"思考-行动-观察"的核心模式
2. **工具系统**：学习 Function Calling 和工具设计
3. **会话管理**：掌握多轮对话和上下文管理
4. **记忆系统**：理解短期和长期记忆的实现
5. **安全机制**：学习 Agent 安全最佳实践
6. **技能系统**：掌握复杂任务的编排和封装

项目设计注重实用性，每个模块都有明确的学习目标，最终产出一个可以写进简历、实际使用的工具。

后续拓展方向覆盖了多模型支持、IDE 集成、多 Agent 协作、知识库构建等高级主题，为持续学习和成长提供了清晰的路径。

---

*文档版本：v1.0*
*创建日期：2026-06-01*
*最后更新：2026-06-01*
