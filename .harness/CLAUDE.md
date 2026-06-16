# CodeMind 工程宪法

## 项目定位
CodeMind 是一个 Java 编程助手 AI Agent，核心是 AgentLoop 状态机驱动的多轮工具调用系统。

## 核心架构规则（必须遵守）

### 1. 模块依赖方向
- `domain` ← `api` ← `core` ← `impl` + `mcp` ← `bootstrap`
- 下层绝不能依赖上层，api 和 domain 零依赖

### 2. AgentLoop 边界
- AgentLoop 只做 while + switch 状态转移，单文件 ≤ 200 行
- 每增一个 `ContinueReason` 必须对应一个 `StateHandler`
- 禁止在 AgentLoop 中直接调用 tool/LLM/context 操作

### 3. 压缩单入口
- 所有压缩必须经过 `ContextCompressionOrchestrator` 唯一入口
- L4 摘要禁止 clearHistory()，必须保留消息 role 结构
- TruncationHook 只做预览不做落盘，落盘由 L3 统一处理

### 4. 线程池规范（阿里强制）
- 禁止 `Executors.newXxx()`，必须 `new ThreadPoolExecutor()`
- 必须有业务含义的 NamedThreadFactory
- 必须有界队列，禁止无界
- 线程池统一在 `core/async/ThreadPoolConfig` 管理

### 5. MCP 安全
- MCP 工具必须适配为 Tool 接口，注册到主 ToolRegistry
- 禁止绕过 Hook 链独立执行

### 6. 测试代码
- `**/src/test/` 不入 git，本地运行
- 测试依赖保留 POM（scope=test）

## 当前告警
- #arch 三条压缩路径冲突 → 必须合并（SlidingWindowManager / TokenBudget / CompactionPipeline 三条路径尚未统一）
- #arch L4 clearHistory() 破坏结构 → 必须改为保留 role（CompactHandler, LoopBreakHandler 中仍在使用 clearHistory）
