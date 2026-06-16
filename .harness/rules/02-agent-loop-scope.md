---
description: AgentLoop 只做状态转移，单文件 ≤ 200 行，不含业务实现。
globs: "**/agent/AgentLoop.java"
---
# AgentLoop 职责边界

## 强制
- AgentLoop 只包含 `while + switch` 状态转移逻辑
- 单文件行数 ≤ 200 行（含 import 和注释）
- 每新增一个 `ContinueReason` 必须对应一个独立的 `StateHandler` 实现类
- 核心 StateHandler 存放在 `pattern/react/` 包下（ThinkHandler, ActHandler），恢复处理器存放在 `recovery/` 包下（CompactHandler, LoopBreakHandler 等）
- 所有 side effect 必须在 StateHandler 内部完成

## 禁止出现在 AgentLoop.java 中
- ❌ `toolRegistry.execute()` / `mcpToolRegistry.executeTool()`
- ❌ `llmClient.chat()` / `llmClient.chatStreamWithTools()`
- ❌ `context.addMessage()` / `context.clearHistory()`
- ❌ `context.setSystemMessage()` / `context.setVariable()`
- ❌ `recoveryManager.recordToolCall()` / `recoveryManager.setAttemptedCompact()`
- ❌ `compactionPipeline.run()` / `compactHistory()`
- ❌ `Executors.newXxx()` / `new ThreadPoolExecutor()`

## 允许出现在 AgentLoop.java 中
- ✅ `isTimeout(startTime)` — 超时检查
- ✅ `iterationCount++` — 迭代计数
- ✅ `handler.handle()` — 委托给 StateHandler

## 追溯
- 源于 #arch-review-001: AgentLoop 834 行 7 项职责，修改任一功能影响所有功能
