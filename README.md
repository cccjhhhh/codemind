# CodeMind - 智能编程助手

> 基于 LLM 的智能编程助手，提供代码审查、文档生成、日志分析等功能。

## 功能特性

- **代码审查 (Code Review)**: 自动分析代码质量、发现潜在问题
- **代码搜索 (Code Search)**: 智能搜索代码库，快速定位代码
- **文档生成 (Doc Gen)**: 自动生成代码文档
- **日志分析 (Log Analysis)**: 解析日志，识别异常模式
- **智能问答 (Q&A)**: 基于代码库的智能问答

## 快速开始

### 环境要求

- Java 17+
- Maven 3.8+

### 构建

```bash
mvn clean package
```

### 运行

```bash
java -jar target/codemind-1.0.0-SNAPSHOT.jar
```

### 配置

在 `~/.codemind/config.yml` 中配置 LLM API：

```yaml
llm:
  provider: openai
  api_key: your-api-key
  model: gpt-4
```

## 项目结构（企业级分层架构）

```
codemind/
├── src/main/java/com/codemind/
│   ├── api/            # 接口定义层
│   │   ├── llm/        # LLM 接口（LLMClient, Message, ToolDefinition...）
│   │   ├── tool/       # 工具接口（Tool, ToolResult）
│   │   ├── skill/      # 技能接口（Skill, SkillContext, SkillResult）
│   │   └── session/    # 会话接口（SessionManager, SessionContext）
│   ├── impl/           # 实现层
│   │   ├── llm/        # OpenAI 等 LLM 实现
│   │   ├── tool/       # 工具实现（FileReaderTool, CodeSearchTool...）
│   │   ├── skill/      # 技能实现（CodeReviewSkill, DocGenSkill...）
│   │   ├── session/    # SessionManagerImpl
│   │   └── safety/     # 安全实现（PermissionGate, SafetyChecker）
│   ├── core/           # 核心引擎（AgentLoop, AgentResult）
│   └── cli/            # 命令行入口
├── src/main/resources/
│   ├── application.yml # 配置文件
│   └── prompts/        # Prompt 模板
└── src/test/           # 测试代码
```

## 学习路径

本项目用于学习 Agent 应用开发，详见 [设计文档](./docs/superpowers/specs/2026-06-01-codemind-design.md)。

## License

MIT
