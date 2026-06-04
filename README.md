# CodeMind - 智能编程助手

> 基于 LLM 的智能编程助手，提供代码审查、文档生成、日志分析等功能。

## 功能特性

- **代码审查 (Code Review)**: 自动分析代码质量、发现潜在问题
- **代码搜索 (Code Search)**: 智能搜索代码库，快速定位代码
- **文档生成 (Doc Gen)**: 自动生成代码文档，支持内容创作意图识别
- **日志分析 (Log Analysis)**: 解析日志，识别异常模式
- **智能问答 (Q&A)**: 基于代码库的智能问答

## CLI 特性

- **思考指示器**: 显示 "∴ Thinking..." 动画
- **进度显示**: 实时显示迭代次数和耗时
- **环境变量支持**: NO_COLOR, CLICOLOR, CLICOLOR_FORCE
- **权限交互**: 危险操作需用户确认

## Skill 路由系统

CodeMind 使用三层路由架构触发 Skill：

1. **否定指标检查**: "看看"、"聊聊"等词不触发 Skill
2. **关键词匹配**: 匹配 SKILL.md 中的触发词，置信度 1.0
3. **语义路由**: LLM 判断意图，置信度 ≥ 0.7 才触发

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
│   │   ├── tool/       # 工具接口（Tool, ToolResult, ToolRegistry）
│   │   ├── skill/      # 技能接口（Skill, SkillRegistry）
│   │   ├── session/    # 会话接口（SessionManager, SessionContext）
│   │   ├── safety/     # 权限接口（Permission, PermissionPrompter, PermissionDecision）
│   │   └── cli/        # CLI 接口（OutputFormatter）
│   ├── dto/            # 数据传输对象层
│   │   └── session/    # 会话 DTO（MessageDto, SessionSnapshotDto 等）
│   ├── impl/           # 实现层
│   │   ├── llm/        # OpenAI 等 LLM 实现
│   │   ├── tool/       # 工具实现（ToolRegistryImpl, FileReaderTool...）
│   │   ├── skill/      # 技能实现（SkillRegistryImpl, CodeReviewSkill...）
│   │   ├── session/    # SessionManagerImpl
│   │   ├── safety/     # 安全实现（PermissionGateImpl, SafetyChecker）
│   │   ├── cli/        # CLI 实现（AnsiStyles, CLIPermissionPrompter...）
│   │   └── bootstrap/  # 依赖注入配置（AppBinder）
│   ├── core/           # 核心引擎（AgentLoop, AgentResult）
│   └── cli/            # 命令行入口（CLI）
├── src/main/resources/
│   ├── application.yml # 配置文件
│   └── prompts/        # Prompt 模板
└── src/test/           # 测试代码
```

## 学习路径

本项目用于学习 Agent 应用开发，详见 [设计文档](./docs/superpowers/specs/2026-06-01-codemind-design.md)。

## License

MIT
