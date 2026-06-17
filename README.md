# CodeMind

<p align="center">
  <img src="https://img.shields.io/badge/Java-17-blue?style=for-the-badge&logo=openjdk&logoColor=white" alt="Java 17">
  <img src="https://img.shields.io/badge/Maven-3.8+-C71A36?style=for-the-badge&logo=apache-maven&logoColor=white" alt="Maven">
  <img src="https://img.shields.io/badge/MCP-SDK-009688?style=for-the-badge" alt="MCP SDK">
</p>

<p align="center">
  <strong>智能编程助手 - AI Agent for Coding</strong>
</p>

<p align="center">
  通过自然语言与代码库交互的智能编程助手，帮助开发者理解代码、执行重构、编写测试、分析日志。
</p>

---

## ✨ 特性

- 🤖 **多模型支持** - 支持 OpenAI、Anthropic、Ollama 等多种 LLM 后端
- 🧠 **ReAct 模式** - 思考-行动循环，智能决策执行
- 💬 **会话持久化** - 多轮对话上下文保持，支持会话恢复
- 🔧 **MCP 协议** - 标准化工具接口，可扩展技能系统
- 🛡️ **权限控制** - 分层安全策略，速率限制，命令拦截
- 📁 **文件操作** - 读取、写入、编辑、搜索、Grep、Glob
- 🌐 **Web 访问** - 网页抓取、内容提取、HTML 解析
- 📊 **会话分析** - 历史记录查询、搜索、导出

## 🚀 快速开始

### 前置要求

- Java 17+
- Maven 3.8+（从源码构建时需要）

### 安装

**方式一：一键安装（推荐）**

```bash
# Windows (PowerShell)
irm https://raw.githubusercontent.com/anthropics/codemind/main/install.ps1 | iex

# Linux / macOS
curl -fsSL https://raw.githubusercontent.com/anthropics/codemind/main/install.sh | bash
```

**方式二：从源码构建**

```bash
git clone https://github.com/anthropics/codemind.git
cd codemind
mvn clean package -DskipTests
java -jar target/codemind-1.0.0-SNAPSHOT.jar
```

### 使用

```bash
# 进入项目目录
cd /path/to/your/project

# 启动交互式对话
codemind

# 指定项目目录
codemind -p /path/to/project

# 查看帮助
codemind --help

# 查看版本
codemind --version
```

## 📁 项目结构

```
├── .harness/                          # AI 编程配置（单一真相源）
├── src/main/java/com/codemind/
├── agent/                    # Agent 核心引擎
│   ├── AgentLoop.java       # 状态机入口
│   ├── engine/              # 工作流编排、Token 预算
│   ├── pattern/react/       # ReAct 模式（Think → Act → Observe）
│   ├── recovery/            # 恢复处理器（重试、循环中断）
│   └── spi/                 # SPI 接口定义
├── bootstrap/               # 应用启动引导
├── common/exception/        # 异常层次结构
├── config/                  # 配置管理
├── context/                 # 压缩系统
│   ├── ContextCompressionOrchestrator.java  # 唯一压缩入口
│   ├── Compactor.java       # 压缩策略接口
│   └── L1/L2/L3*.java       # 各级压缩实现
├── frontend/                # 前端展示层
│   ├── cli/                 # CLI 实现
│   ├── output/spi/          # 输出格式化接口
│   └── style/               # ANSI 样式定义
├── llm/                     # LLM 客户端（OpenAI、Anthropic、Ollama）
├── mcp/                     # MCP 协议实现
├── safety/                  # 安全控制（权限门、速率限制）
├── session/                 # 会话管理（持久化、多轮对话）
├── skill/                   # 技能系统（加载、路由、注册）
│   ├── routing/             # 关键词路由、置信度路由
│   └── spi/                 # 技能接口
└── tool/                    # 工具系统
    ├── spi/Tool.java        # 工具接口
    ├── ToolRegistry.java    # 工具注册中心
    ├── impl/                # 工具实现（Read、Write、Edit、Bash、Grep、Glob 等）
    └── hook/                # 工具钩子（安全、权限、指标、截断）
```

## ⚙️ 配置

配置文件位于 `~/.codemind/settings.json`：

```json
{
  "llm": {
    "provider": "openai",
    "apiKey": "sk-xxx",
    "model": "gpt-4",
    "baseUrl": "https://api.openai.com/v1"
  },
  "session": {
    "maxHistory": 50,
    "autoSave": true
  },
  "safety": {
    "permissionMode": "interactive",
    "rateLimitPerMinute": 30
  }
}
```

### 支持的 LLM 提供商

| 提供商 | 模型示例 | 配置字段 |
|--------|----------|----------|
| OpenAI | gpt-4, gpt-3.5-turbo | `provider: "openai"` |
| Anthropic | claude-3-opus, claude-3-sonnet | `provider: "anthropic"` |
| Ollama | llama2, codellama | `provider: "ollama"` |

## 🔧 常用命令

| 命令 | 说明 |
|------|------|
| `codemind` | 启动交互式对话 |
| `codemind -p <path>` | 指定工作目录 |
| `codemind --version` | 显示版本号 |
| `codemind --help` | 显示帮助信息 |

### 对话示例

```
> 帮我分析一下这个项目的架构

> 把 UserService 中的 getUserById 方法重构一下

> 为 src/utils 目录下的工具函数编写单元测试

> 分析 logs/app.log 中的错误日志
```

## 🛡️ 安全机制

- **权限门（PermissionGate）** - 分层访问控制，区分只读/写入/破坏性操作
- **速率限制（RateLimiter）** - Token Bucket 算法，防止 API 滥用
- **命令拦截（CommandInterceptor）** - 危险命令拦截（rm -rf、DROP TABLE 等）
- **安全钩子（SafetyPreHook）** - 工具执行前的安全检查

## 🧩 扩展系统

### 技能（Skills）

技能是可复用的任务模板，定义了特定场景的工作流程：

```bash
# 内置技能
analyzw_log     日志分析
code_review     代码审评
```

### 工具钩子（Tool Hooks）

工具钩子在工具执行前后触发：

```java
public class MyHook implements ToolHook {
    @Override
    public void beforeExecution(ToolContext context) {
        // 执行前逻辑
    }
    
    @Override
    public void afterExecution(ToolContext context, ToolResult result) {
        // 执行后逻辑
    }
}
```

## 📚 相关文档

- [安装指南](docs/installation.md) - 详细的安装步骤
- [工程宪法](.harness/src/prompts/system.md) - 项目架构规则与约束

---

<p align="center">
  用 ❤️ 构建 | Powered by Java 17 + MCP Protocol
</p>
