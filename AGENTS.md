# CodeMind - AGENTS.md

> 学习型 Agent 应用项目。中文注释为主，用于理解 Agent 开发核心概念。

## 项目状态

**早期开发阶段**。大多数核心类都只有 `throw new UnsupportedOperationException("Not implemented yet")`。预计存在大量未实现的方法和 TODO 注释。

## 构建与运行

```bash
# 构建（生成包含所有依赖的 shaded JAR）
mvn clean package

# 运行
java -jar target/codemind-1.0.0-SNAPSHOT.jar

# 或直接运行
mvn exec:java -Dexec.mainClass="com.codemind.CodeMindApplication"
```

入口类：`com.codemind.CodeMindApplication`

## 配置

两个配置位置：

| 文件 | 用途 |
|------|------|
| `src/main/resources/application.yml` | 应用默认配置（模型、超时、允许的命令） |
| `~/.codemind/config.yml` | 用户 LLM API 凭证 |

用户配置格式：
```yaml
llm:
  provider: openai
  api_key: your-api-key
  model: gpt-4
```

环境变量 `OPENAI_API_KEY` 在默认配置中被引用。

## 项目结构（企业级分层架构）

```
src/main/java/com/codemind/
├── api/                         # 接口定义层（Interface）
│   ├── llm/                    # LLM 客户端接口、消息、工具定义
│   ├── tool/                   # 工具接口（Tool, ToolResult, ToolRegistry）
│   ├── skill/                  # 技能接口（Skill, SkillRegistry）
│   ├── session/                # 会话管理器接口、会话上下文
│   ├── safety/                 # 权限接口（Permission, PermissionPrompter, PermissionDecision, PermissionGate）
│   └── cli/                    # CLI 接口（OutputFormatter）
├── dto/                         # 数据传输对象层（⭐ 新增）
│   └── session/                 # 会话相关 DTO（MessageDto, SessionSnapshotDto 等）
├── impl/                        # 实现层（Implementation）
│   ├── llm/                    # OpenAI 等 LLM 实现
│   ├── tool/                   # 工具实现（ToolRegistryImpl, FileReaderTool, FileWriterTool...）
│   ├── skill/                  # 技能实现（SkillRegistryImpl, CodeReviewSkill...）
│   ├── session/                # SessionManagerImpl
│   ├── safety/                 # 安全实现（PermissionGateImpl, SafetyChecker）
│   ├── cli/                    # CLI 实现（AnsiStyles, DefaultOutputFormatter, CLIPermissionPrompter）
│   └── bootstrap/              # 依赖注入配置（AppBinder）⭐ 新增
├── core/                        # 核心引擎
│   ├── AgentLoop.java          # Agent 循环引擎（ReAct 模式，支持权限确认）
│   └── AgentResult.java        # Agent 执行结果
├── cli/                         # 命令行入口
│   └── CLI.java                # Picocli CLI 应用
└── CodeMindApplication.java    # 主程序入口
```

## 核心概念

- **AgentLoop**：实现"思考-行动-观察"（Think-Act-Observe）的 ReAct 模式，支持工具执行权限确认
- **ToolRegistry**：管理所有工具的注册与执行，支持权限检查
- **PermissionGate**：权限网关接口，控制危险操作的执行权限（WRITE_FILE, EXECUTE_COMMAND）
- **PermissionPrompter**：权限询问接口，CLI 实现支持 y/n/session 交互
- **Skills**：高级任务编排（多工具协作的工作流）
- **LLMClient**：LLM API 交互封装，支持同步/流式/Function Calling
- **AppBinder**：依赖注入配置，集中管理依赖创建

详细概念说明见设计文档：`docs/superpowers/specs/2026-06-01-codemind-design.md`

## 技术栈

| 类别 | 技术 |
|------|------|
| HTTP | OkHttp 4.12 + okhttp-sse（流式响应） |
| JSON | Jackson 2.16（databind + yaml） |
| CLI | Picocli 4.7 |
| 配置 | SnakeYAML 2.2 |
| 日志 | SLF4J + Logback |
| 测试 | JUnit 5 + Mockito（尚未使用） |

## 测试

暂无测试。`src/test/java/` 目录为空。

添加测试时使用 JUnit 5 + Mockito 模式：
```java
@ExtendWith(MockitoExtension.class)
class SomeTest {
    @Mock
    LLMClient llmClient;
    
    @InjectMocks
    AgentLoop agentLoop;
}
```

## 代码规范

- 源代码使用中文注释（项目用于学习 Agent 开发）
- Java 17 标准规范
- 接口优先设计（`Tool`、`Skill`、`LLMClient` 等都使用接口）
- 依赖使用构造器注入
- 企业级分层：`api/` 定义接口，`impl/` 实现

## 设计文档

必读：理解项目意图的核心文档

`docs/superpowers/specs/2026-06-01-codemind-design.md`

包含：
- 每个模块存在的意义（学习目标）
- Agent 核心概念映射
- 分阶段开发计划
- 未来扩展方向

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
| `dto/` | 数据传输对象 | 无业务逻辑依赖 |
| `impl/` | 接口实现 | api/, 外部依赖 |
| `bootstrap/` | 依赖注入配置 | api/, impl/ |
| `core/` | 核心引擎 | api/, 外部依赖 |
| `cli/` | 命令行入口 | core/, api/, bootstrap/ |

**禁止**：
- api → impl（接口依赖实现）
- core → impl（核心依赖实现）
- dto → impl/（DTO 不应依赖实现）

### 架构约束（强制执行）

#### 1. 单一职责原则（SRP）

**每个类只负责一个职责。** 发现类过大或承担多种职责时，必须拆分。

| 违规场景 | 正确做法 |
|----------|----------|
| 实现类中定义多个内部类（如 DTO） | 将 DTO 移到 `dto/` 包独立文件 |
| 类承担 UI 渲染 + 业务逻辑 | 拆分为 UI 类 + Service 类 |
| 内部类实现接口 | 提取为独立类 |

**示例**：
```java
// ❌ 错误：SessionManagerImpl 定义了 4 个内部类
public class SessionManagerImpl {
    public class SessionInfo { ... }      // 应移至 dto/session/SessionInfoDto.java
    public class SessionSnapshot { ... }  // 应移至 dto/session/SessionSnapshotDto.java
    public class MessageDto { ... }       // 应移至 dto/session/MessageDto.java
    public class ToolCallDto { ... }     // 应移至 dto/session/ToolCallDto.java
}

// ✅ 正确：每个类独立，职责单一
public class SessionManagerImpl { ... }  // 只负责会话管理
```

#### 2. 依赖倒置原则（DIP）

**高层模块依赖接口，不依赖实现。**

```java
// ❌ 错误：依赖具体实现类
public SkillRegistryImpl {
    public SkillRegistryImpl(ToolRegistryImpl toolRegistry) { ... }
}

// ✅ 正确：依赖接口
public SkillRegistryImpl {
    public SkillRegistryImpl(ToolRegistry toolRegistry) { ... }
}
```

#### 3. 接口分离原则（ISP）

**接口应该小而专注，客户端不应该依赖不使用的方法。**

| 违规场景 | 正确做法 |
|----------|----------|
| 一个接口包含过多方法 | 拆分为多个小接口 |
| 内部类实现接口 | 提取为独立类 |

#### 4. DTO 设计规范

| 规范 | 说明 |
|------|------|
| 位置 | `dto/<module>/` 包下 |
| 命名 | `XxxDto` 后缀 |
| 职责 | 只做数据传输，无业务逻辑 |
| 序列化 | 使用 Jackson 注解 |

**禁止在实现类内部定义 DTO 类。**

#### 5. 依赖注入规范

| 规范 | 说明 |
|------|------|
| 注入方式 | 构造器注入 |
| 配置位置 | `bootstrap/` 包 |
| 创建顺序 | 按依赖关系排序 |

**示例**：
```java
// ✅ 正确：在 bootstrap/ 包中集中管理依赖创建
public class AppBinder {
    public ToolRegistry createToolRegistry(PermissionGate permissionGate) {
        ToolRegistryImpl registry = new ToolRegistryImpl(permissionGate);
        // ... 注册工具
        return registry;
    }
}
```

#### 6. 方法引用检查规范（⭐ 新增）

**未被引用的方法必须阐述保留理由。**

```java
// ❌ 错误：方法未被任何代码调用，且无注释说明
public class SomeService {
    public void unusedMethod() {
        // 这个方法没有被任何地方调用
        // 编码者应删除，或在注释中说明为何保留
    }
}

// ✅ 正确：未被引用但保留的方法，添加注释说明理由
public class SomeService {
    /**
     * 预留方法 - 供未来扩展使用
     * 
     * 保留理由：
     * 1. 下一版本将实现批量导入功能，此方法为批量导入的入口
     * 2. API 设计需要保持接口稳定，预留此方法避免破坏性变更
     * 3. 计划于 2026-Q3 实现此功能
     */
    public void batchImport(List<Item> items) {
        throw new UnsupportedOperationException("计划于 2026-Q3 实现");
    }
}
```

| 检查时机 | 操作 |
|----------|------|
| 新增方法时 | 确认方法会被调用，或添加保留理由注释 |
| 审查代码时 | 使用 IDE 或 grep 检查方法引用，删除无理由保留的死代码 |
| 重构时 | 移除未被引用且无保留理由的方法 |

**检查命令**：
```bash
# 检查方法引用（需人工确认）
grep -r "methodName" src/main/java/
```

### 编码规范

1. **命名**：类名 PascalCase，方法名 camelCase
2. **接口优先**：变量使用接口类型声明
3. **构造器注入**：依赖通过构造函数注入
4. **中文注释**：解释"为什么"而非"是什么"
5. **禁止内部类实现接口**：内部类只应用于辅助外部类工作，不应实现接口；如需实现接口，必须提取为独立类
6. **禁止在实现类中定义 DTO**：DTO 类必须放在 `dto/` 包下的独立文件中

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