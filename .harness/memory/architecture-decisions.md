# CodeMind 架构决策记录 (ADR)

## ADR-001: 多模块拆分

**决策**：从单模块拆分为 6 模块（api/domain/core/impl/mcp/bootstrap）
**理由**：当前 93 个 Java 文件集中在单模块中，无依赖约束。多模块使依赖方向由编译器强制保证。
**日期**：2026-06-12
**状态**：待实施

---

## ADR-002: AgentLoop 状态机瘦身

**决策**：AgentLoop 从 834 行降至 ≤ 200 行，只保留 while + switch
**理由**：7 项职责耦合导致修改任一功能影响所有功能
**关键拆分**：
- `queryLoop()` → `WorkflowOrchestrator`
- `runSingleTurn()` → `LLMOrchestrator`
- `executeBatch*()` → `ToolExecutionService`
- `compactHistory()` → `ContextCompressionOrchestrator`
- `cacheReadResult()` → `FileContentCache`
**日期**：2026-06-12
**状态**：待实施

---

## ADR-003: 压缩管线单入口

**决策**：所有压缩通过 `ContextCompressionOrchestrator` 单一入口
**理由**：当前 getManagedHistory → TokenBudget → CompactionPipeline 三条路径独立运行
**关键变更**：
- SlidingWindowManager 只做窗口裁剪，不做工具结果占位
- TruncationHook 只做预览，不做落盘
- L3 统一负责大结果落盘
- L4 保留消息 role 结构，禁止 clearHistory
**日期**：2026-06-12
**状态**：待实施

---

## ADR-004: MCP 工具统一注册

**决策**：MCP 工具适配为 Tool 接口后注册到主 ToolRegistry
**理由**：当前 McpToolRegistry 独立执行，绕过 SafetyPreHook 和 PermissionPreHook
**日期**：2026-06-12
**状态**：待实施

---

## ADR-005: 线程池统一管理

**决策**：所有线程池集中在 ThreadPoolConfig，使用 ThreadPoolExecutor 直接创建
**理由**：当前 executeBatchParallel 每次调用新建线程池，违反阿里规约
**日期**：2026-06-12
**状态**：待实施
