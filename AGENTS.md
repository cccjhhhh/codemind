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
├── api/                    # 接口定义层（Interface）
│   ├── llm/               # LLM 客户端接口、消息、工具定义
│   ├── tool/              # 工具接口、工具结果
│   ├── skill/             # 技能接口、技能上下文、技能结果
│   └── session/           # 会话管理器接口、会话上下文
├── impl/                   # 实现层（Implementation）
│   ├── llm/               # OpenAI 等 LLM 实现
│   ├── tool/              # FileReaderTool、CodeSearchTool、CommandRunnerTool、LogParserTool
│   ├── skill/             # CodeReviewSkill、DocGenSkill、LogAnalysisSkill、SkillRegistry
│   ├── session/           # SessionManagerImpl
│   └── safety/            # PermissionGate、SafetyChecker、Permission 枚举
├── core/                   # 核心引擎
│   ├── AgentLoop.java     # Agent 循环引擎（ReAct 模式）
│   └── AgentResult.java   # Agent 执行结果
├── cli/                    # 命令行入口
│   └── CLI.java           # Picocli CLI 应用
└── CodeMindApplication.java # 主程序入口
```

## 核心概念

- **AgentLoop**：实现"思考-行动-观察"（Think-Act-Observe）的 ReAct 模式
- **ToolRegistry**：管理所有工具的注册与执行
- **Skills**：高级任务编排（多工具协作的工作流）
- **LLMClient**：LLM API 交互封装，支持同步/流式/Function Calling

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