# CodeMind 工程宪法

## 项目定位
CodeMind 是一个 Java 编程助手 AI Agent，核心是 AgentLoop 状态机驱动的多轮工具调用系统。

## 核心架构规则（必须遵守）

### 1. AgentLoop 边界
- AgentLoop 只做 while + switch 状态转移，单文件 ≤ 200 行
- 每增一个 ContinueReason 必须对应一个 StateHandler
- 禁止在 AgentLoop 中直接调用 tool/LLM/context 操作

### 2. 压缩单入口
- 所有压缩必须经过 ContextCompressionOrchestrator 唯一入口
- L4 摘要禁止 clearHistory()，必须保留消息 role 结构

### 3. 线程池规范（阿里强制）
- 禁止 Executors.newXxx()，必须 new ThreadPoolExecutor()
- 必须有业务含义的 NamedThreadFactory
- 必须有界队列，禁止无界

### 4. MCP 安全
- MCP 工具必须适配为 Tool 接口，注册到主 ToolRegistry
- 禁止绕过 Hook 链独立执行

## 项目结构
```
src/main/java/com/codemind/
├── agent/              # AgentLoop 状态机
│   ├── AgentLoop.java  # 主循环，≤ 200 行
│   ├── engine/         # WorkflowOrchestrator, TokenBudget
│   ├── pattern/react/  # ThinkHandler, ActHandler
│   └── recovery/       # CompactHandler, LoopBreakHandler
├── context/            # 压缩系统
│   ├── ContextCompressionOrchestrator.java  # 唯一压缩入口
│   ├── Compactor.java  # 压缩策略接口
│   └── L1/L2/L3*.java  # 各级压缩实现
├── tool/               # 工具系统
│   ├── spi/Tool.java   # 工具接口
│   ├── ToolRegistry.java
│   └── impl/           # Read, Write, Edit, Bash, Grep, Glob 等
├── llm/                # LLM 客户端
├── mcp/                # MCP 协议
├── safety/             # 权限门、速率限制
├── session/            # 会话持久化
├── skill/              # 技能系统
├── frontend/           # CLI + 输出格式化
├── config/             # 配置管理
├── bootstrap/          # 启动引导
└── common/exception/   # AgentException 层次
```

## Build & Test
```bash
# 构建
mvn clean package -DskipTests

# 运行测试
mvn test

# 运行单个测试
mvn test -Dtest=ClassName

# 启动应用
java -jar target/codemind-1.0.0-SNAPSHOT.jar
```

## 代码风格
- Java 17，使用 record、sealed class、pattern matching
- 异常继承 AgentException（com.codemind.common.exception）
- 日志使用 SLF4J + Logback
- 测试代码不入 git（src/test/ 加入 .gitignore）
