---
name: codemind-optimization-design
description: CodeMind 系统优化设计——稳定性维修、权限统一、上下文四层压缩管线、新循环体系、Skill 触发增强、闭环运营
metadata:
  type: design-spec
  status: draft
  version: 1.0
---

# CodeMind 系统优化设计

## 概述

对 CodeMind 编程助手进行系统性优化，覆盖 6 个改进区域，分 3 个 Phase 执行：

- **Phase 1A**：稳定性维修（5 项 Bug Fix）
- **Phase 1B**：权限模型统一（3 项清理）
- **Phase 2C**：上下文增强——四层压缩管线 + 新循环体系
- **Phase 2D**：Skill 触发增强——双通道路由
- **Phase 3E**：闭环运营——指标 + 回归

**核心设计原则**：
1. **便宜的先跑，贵的后跑**（Cheap First, Expensive Last）——零 API 调用的文本操作优先，LLM 摘要最后
2. **循续理由驱动**（Continuation Reason）——不再靠硬编码 maxIterations 控制循环，而是语义化状态机
3. **可观测先于可优化**——先埋点再优化，用数据驱动决策

---

## Phase 1A：稳定性维修

### ① AgentLoop latch 死锁（AgentLoop.java:273）

**问题**：`latch.await()` 无超时。LLM 流式回调异常未触发 countDown 时，线程永久挂起。

**改动**：
```java
// 改前
latch.await();

// 改后
if (!latch.await(30, TimeUnit.SECONDS)) {
    throw new TimeoutException("LLM 流式响应超时（30s）");
}
```
并在外层 catch 链中处理 TimeoutException，返回可读错误而非抛 RuntimeException。

### ② CLI /load 会话不生效（CLI.java:582-586）

**问题**：`loadedContext` 赋给局部变量后丢弃，主循环一直在用旧的 `context`。

**改动**：
```java
// 外层：context 声明为可替换的引用
SessionContext[] contextHolder = new SessionContext[]{bootResult.session()};

// handleLoadCommand 中替换
contextHolder[0] = loadedContext;

// actionLoop 调用时
AgentResult result = agentLoop.runStream(input, contextHolder[0], ...);
```

### ③ CLI --config/--verbose 未接入（CLI.java）

**改动**：
- `--config` 传给 `SettingsLoader.loadChain(projectDir, configPath)`，使支持指定配置文件路径
- `--verbose` 设为 `DefaultOutputFormatter` 的详细输出开关

### ④ Bootstrapper 硬编码 50/300（CodeMindBootstrapper.java:118）

**改动**：
- `bootstrap()` 方法加参数 `maxIterations` 和 `timeoutSeconds`（有默认值 50/300 向下兼容）
- CLI 启动时传入命令行参数值
- settings.json 新增 `agent.maxIterations` / `agent.timeoutSeconds`（CLI 参数覆盖 settings，settings 覆盖默认值）

### ⑤ SSE 异常处理（AgentLoop.java:280-285）

**问题**：SSE 错误直接 `throw new RuntimeException(...)`，未给 LLM 恢复机会。

**改动**：`runSingleTurn()` 返回带错误标记的 `AgentTurnResult`，错误时注入 system message 提示 LLM 重试而非直接终止循环。

---

## Phase 1B：权限模型统一

### ① 删除 `Tool.getDefaultPermission()`

**问题**：该接口方法在所有 10+ 实现中均有定义，但 `PermissionPreHook` 不走这个方法——它走 `PermissionGateImpl.DEFAULT_LEVELS` map。所有实现均为死代码。

**改动**：从 `Tool` 接口删除 `getDefaultPermission()`，删除所有工具类中的对应实现。保留 `getDeprecatedName()`（用于 ToolRegistryImpl 别名映射）。

### ② PermissionGateImpl 补完

**当前状态**：`PermissionPreHook` 已正确调用 `PermissionGateImpl.getDefaultLevel()`，权限链路是通的。但：
- 规则中的 `condition` 字段从未执行
- settings 中的 `deny` 列表未处理

**改动**：
- `PermissionGateImpl` 新增 `denyPatterns` 字段（`List<Pattern>`），settings 中的 `deny` 正则在这里执行硬拒绝
- `PermissionRule.condition` 字段暂不执行，加 `@Deprecated` 标注为扩展点
- 检查顺序：runtimeLevels → denyPatterns → rules → DEFAULT_LEVELS

### ③ SafetyChecker 冗余方法删除 + SafetyPreHook 单一入口确认

**改动**：删除 SafetyChecker 中 6 个未调用方法（`isDangerousCommand`、`isWithinAllowedDir`、`sanitizePath` 等）。确认 `SafetyPreHook` 是全局危险命令检测的唯一入口。

---

## Phase 2C：上下文增强——四层压缩管线 + 新循环体系

### 核心参考

参考实现：
1. [s08_context_compact](https://github.com/shareAI-lab/learn-claude-code/tree/main/s08_context_compact) ——四层压缩管线
2. [Claude Code QueryEngine](https://github.com/luyao618/Claude-Code-Source-Study/blob/main/docs/05-QueryEngine%E4%B8%8E%E5%AF%B9%E8%AF%9D%E4%B8%BB%E5%BE%AA%E7%8E%AF.md) ——Continuation Reason + Recovery 循环

### 新循环体系架构

#### 三层分层

| 层 | 类 | 职责 |
|----|-----|------|
| Facade | `CLI.java` | 用户交互、session 状态、命令处理 |
| Kernel | `AgentLoop` → `queryLoop()` | while(true) 状态机，每次迭代: 预处理→调 API→执行工具→评估理由 |
| Cross-cutting | `QueryConfig`、`ContinueReason`、`StopHook`、`TokenBudget`、`CompactionPipeline`、`ToolPartitioner` | 横切关注点 |

#### ContinueReason 枚举

```java
enum ContinueReason {
    NEXT_TURN,              // 工具结果返回，正常继续下一轮
    TOKEN_BUDGET_CONTINUE,  // 模型因预算截断（max_tokens），nudge 继续
    RECOVERY_COMPACT,       // prompt_too_long，压缩后重试
    RECOVERY_ESCALATE,      // max_output_tokens，升级限额后重试
    RECOVERY_FAILOVER,      // 连续 529 过载，切 fallback 模型
    COMPLETE,               // 模型返回最终回答，正常结束
    MAX_ITERATIONS,         // 超过迭代上限（熔断）
    ERROR,                  // 不可恢复错误
    USER_INTERRUPT          // 用户中断
}
```

#### 循环体伪代码

```
State state = new State(messages, ContinueReason.NEXT_TURN);

while (true) {
    switch (state.reason) {
        case COMPLETE:
            return success(state.output);

        case ERROR, MAX_ITERATIONS, USER_INTERRUPT:
            return failure(state.reason, state.error);

        case RECOVERY_COMPACT:
            compactHistory(state.messages);  // L4
            state.reason = ContinueReason.NEXT_TURN;
            continue;  // 重试

        case RECOVERY_ESCALATE:
            escalateOutputTokens(state);
            state.reason = ContinueReason.NEXT_TURN;
            continue;

        case RECOVERY_FAILOVER:
            switchFallbackModel(state);
            state.reason = ContinueReason.NEXT_TURN;
            continue;

        case TOKEN_BUDGET_CONTINUE:
            // 注入 nudge 消息，继续
            injectNudgeMessage(state);
            // fall-through

        case NEXT_TURN:
            state = queryLoop(state);  // 核心的一次迭代
    }
}

queryLoop(state):
    // 1. 冻结当前配置快照（maxIterations/timeout/contextWindow）
    QueryConfig config = freezeConfig();

    // 2. 消息预处理器（L3→L1→L2→L4）
    state.messages = compactionPipeline.run(state.messages, config);

    // 3. 调 LLM API
    Response response = llmClient.chat(state.messages, tools);

    // 4. 无工具调用 → 评估是否完成
    if (!response.hasToolCalls()) {
        return stopHooks.evaluate(response, state, config);
    }

    // 5. 分拆工具（读写分离）
    List<Batch> batches = toolPartitioner.partition(response.toolCalls);

    // 6. 执行工具（并行的并行，串行的串行）
    for (Batch batch : batches) {
        if (batch.isParallel()) {
            parallelExec(batch.toolCalls);
        } else {
            serialExec(batch.toolCalls);
        }
    }

    // 7. 注入附件（文件变更通知、Memory 预取结果等，未来扩展）

    // 8. 返回 NEXT_TURN
    return State(state.messages, ContinueReason.NEXT_TURN);
```

### maxIterations 的角色变化

**旧行为**：`for (iteration < maxIterations)` 作为唯一循环终止条件，iteration 每次工具调用后 +1。

**新行为**：`maxIterations` 降级为熔断阈值——只是一个安全网（default=100），正常循环由 `ContinueReason.COMPLETE` 终止。只有连续 `NEXT_TURN` 超过阈值时触发 `MAX_ITERATIONS`。recovery 路径不计入迭代计数。

### 消息预处理管线

管线在每次 API 调用前执行，顺序固定：

```
L3: toolResultBudget (大结果落盘)       ← 零 API 调用，文件 I/O
  ↓
L1: snipCompact (裁剪中间旧消息)        ← 零 API 调用，列表切片
  ↓
L2: microCompact (旧结果占位)          ← 零 API 调用，字符串替换
  ↓
  [检查 token 是否仍超阈值?]
  ↓ 是
L4: compactHistory (LLM 摘要)          ← 1 次 API 调用（非流式）
  ↓
  [检查错误?]
  ↓ prompt_too_long
reactiveCompact (应急压缩)              ← 1 次 API 调用，保留尾部 5 条
  ↓ 仍失败
  抛不可恢复错误 → ContinueReason.ERROR
```

### L4 compactHistory 详细设计

**触发条件**：L1+L2+L3 全部执行完毕，token 仍超过 `(maxContextTokens - reservedResponseTokens) * targetRatio`。

**摘要调用**：使用 `LLMClient.chat()`（同步非流式），不走 AgentLoop 迭代。

**摘要 prompt 模板**：
```
Summarize this coding-agent conversation so work can continue.
Preserve:
1. Current goal / 当前目标
2. Key findings and decisions / 关键发现与决策
3. Files read and changed / 已读改文件列表
4. Remaining work / 剩余工作
5. User constraints and preferences / 用户约束

Conversation:
{conversation_json}
```

**摘要结果**：替换消息列表为一条 `user` 角色消息，内容为 `[Compacted]\n\n{summary}`。

**熔断**：连续 3 次摘要失败（API error、空摘要）→ 不再重试 L4，fallback 到 L1+L2 的现有裁剪策略。

**transcript 保存**：摘要前完整对话写入 `.codemind/transcripts/{sessionId}-{timestamp}.jsonl`。

### reactiveCompact 应急压缩

**触发**：`OpenAIClient` 收到 `prompt_too_long` / `context_length_exceeded` 400 错误。

**行为**：保存 transcript → LLM 摘要 → 保留尾部最多 5 条最近消息（保持 tool_use/tool_result 配对完整性）→ 替换消息列表。

**重试上限**：1 次。超出则抛不可恢复错误。

### ToolPartitioner 工具分区

依据工具的安全性做分区，参考 Claude Code 的 `partitionToolCalls()`：

| 类别 | 工具 | 执行模式 |
|------|------|---------|
| Read-safe | Read, Grep, Glob, WebFetch | 并行 (max concurrency=5) |
| Serial | Write, Edit, Bash, Todo, Task | 串行（逐条执行） |

分区算法：保留原始顺序，连续 read-safe 工具合并为一个并行 batch，每个 serial 工具独立为一个 batch。

```
[Read, Grep, Write, Glob, Bash] 
  → [Read, Grep] (parallel) → [Write] (serial) → [Glob] (serial) 
  → [Bash] (serial)
```

### QueryConfig 配置冻结

在每次 `queryLoop()` 入口处冻结配置快照，防止迭代中配置变化导致不一致：

```java
record QueryConfig(
    int maxIterations,
    long deadlineMs,           // 绝对截止时间
    int contextWindowTokens,
    double targetRatio,
    int reservedResponseTokens,
    int maxOutputTokens,       // 默认 8192，RECOVERY_ESCALATE 时升至 65536
    boolean hasAttemptedReactiveCompact
) {}
```

### 上下文配置调整

settings.json 新增字段：

```json
{
  "agent": {
    "maxIterations": 100,
    "timeoutSeconds": 600
  },
  "context": {
    "truncation": {
      "spill_threshold_chars": 2000,
      "spill_dir": ".codemind/spill"
    },
    "window": {
      "target_ratio": 0.85,
      "stale_rounds": 5
    },
    "compaction": {
      "max_messages_before_snip": 50,
      "keep_recent_tool_results": 3,
      "budget_max_bytes": 200000,
      "compact_on_ratio": 0.9,
      "max_consecutive_failures": 3,
      "save_transcripts": true
    }
  }
}
```

**说明**：
- `compact_on_ratio`：触发 L4 的阈值（L1-3 后 token 超过这个比例时触发），默认 0.9
- 相比原来的 `target_ratio=0.8`，提高至 0.85 以利用大窗口模型

---

## Phase 2D：Skill 触发增强——双通道路由

### 设计

在现有 `KeywordSkillRouter` 外层包裹 `ConfidenceSkillRouter`：

```
ConfidenceSkillRouter.route(input):
  → 规则匹配（关键词多级打分）
  → score >= 0.7 → 自动激活
  → 0.3 <= score < 0.7 → 免激活（KeywordRouter 兜底）
  → score < 0.3 → 跳过

KeywordSkillRouter.route(input):
  → 关键词精确匹配
  → 命中则激活
  → 不命中返回 null
```

置信度打分规则（不引入 ML 模型，纯规则）：

| 规则 | 加分 |
|------|------|
| 输入包含 skill 关键词 | +0.5 |
| 输入包含 2 个以上关键词 | +0.3 |
| 输入包含上一轮 tool 结果中的词 | +0.2 |
| 输入是上一轮的连续追问 | -0.1 |
| 输入长度 < 5 chars | -0.2 |

### 评估数据采集

每次路由结果写日志行到 `.codemind/metrics/skill_routing.log`（JSONL 格式）：

```json
{
  "input": "解释这段代码",
  "confidence": 0.7,
  "route": "Hit",
  "matchedSkill": "code_explain",
  "userAccepted": true,
  "timestamp": "2026-06-10T12:00:00"
}
```

路由分类：
- `Hit`：置信度 >= 0.7，skill 正确激活
- `Miss`：置信度 < 0.7 但 KeywordRouter 命中（低置信度仍需 Review）
- `FalseAlarm`：置信度 >= 0.7 但 LLM 对话中未实际使用该 skill

---

## Phase 3E：闭环运营

### 核心指标

| 指标 | 数据源 | 埋点位置 |
|------|-------|---------|
| 任务成功率 | AgentResult.isSuccess() | MetricsHook |
| 平均迭代轮次 | state.iteration count | AgentLoop |
| 工具失败率 | ToolResult.isSuccess() | MetricsHook |
| 拒绝率 | PermissionPreHook.SecurityException | PermissionPreHook |
| 人工打断率 | ContinueReason.USER_INTERRUPT | AgentLoop |
| L4 摘要触发率 | compactHistory 调用次数 | SlidingWindowContextManager |

### 埋点输出

每次 `agentLoop.runStream()` 结束写入一行到 `.codemind/metrics/session/{sessionId}.jsonl`：

```json
{
  "sessionId": "...",
  "success": true,
  "totalIterations": 12,
  "totalElapsedMs": 45320,
  "toolCalls": {"Bash": 3, "Read": 5, "Write": 1, "Edit": 1, "Grep": 2},
  "toolFailures": 0,
  "permissionDenials": 0,
  "L4Triggered": false,
  "recoveryCount": 0,
  "model": "deepseek-chat",
  "timestamp": "2026-06-10T12:00:00"
}
```

### 回归用例

在 `src/test/java/com/codemind/regression/` 下新增：

| 用例 | 验证内容 |
|------|---------|
| `AgentLoopDeadlockTest` | latch 超时后正常返回错误而非卡死 |
| `PermissionPreHookTest` | DENY 规则正确拒绝，ALLOW 规则正确放行 |
| `CompactionPipelineTest` | L1-L4 各层触发条件与行为 |
| `ToolPartitionerTest` | 读写分离正确分区 |
| `SkillRoutingTest` | 置信度路由 + KeywordRouter 兜底 |

---

## 优先级与依赖关系

```
Phase 1A + 1B (并行，无外部依赖)
  │
  ▼
Phase 2C (依赖 Phase 1A 的稳定性修复——新循环体系建立在稳定基础上)
  │
  ▼
Phase 2D (独立，可并行于 2C)
  │
  ▼
Phase 3E (依赖 Phase 1-2 全部完成——需要真实的运行数据作为基线)
```

## 文件变更总览

| 文件 | 操作 | 归属 |
|------|------|------|
| `AgentLoop.java` | 重写（新循环体系） | Phase 2C |
| `ContinueReason.java` | 新增 | Phase 2C |
| `QueryConfig.java` | 新增 | Phase 2C |
| `StopHook.java` | 新增 | Phase 2C |
| `TokenBudget.java` | 新增 | Phase 2C |
| `CompactionPipeline.java` | 新增 | Phase 2C |
| `ToolPartitioner.java` | 新增 | Phase 2C |
| `SlidingWindowContextManager.java` | 重写（四层管线） | Phase 2C |
| `SessionContext.java` | 新增 compactHistory 支持字段 | Phase 2C |
| `OpenAIClient.java` | 新增 prompt_too_long 检测 | Phase 2C |
| `LLMClient.java` | 不修改（已有 chat() 方法） | — |
| `CLI.java` | 修改（/load、config、verbose 修复） | Phase 1A |
| `CodeMindBootstrapper.java` | 修改（参数化 bootstrap） | Phase 1A |
| `Settings.java` | 新增 agent/compaction 配置 | Phase 1A + 2C |
| `Tool.java` | 删除 getDefaultPermission() | Phase 1B |
| `BashTool.java` | 删除 getDefaultPermission() | Phase 1B |
| `ReadTool/WriteTool/EditTool/...` | 删除 getDefaultPermission() | Phase 1B |
| `PermissionGateImpl.java` | 新增 denyPatterns 支持 | Phase 1B |
| `SafetyChecker.java` | 删除 6 个未调用方法 | Phase 1B |
| `MetricsHook.java` | 增强埋点字段 | Phase 3E |
| `ConfidenceSkillRouter.java` | 新增 | Phase 2D |
| `KeywordSkillRouter.java` | 不修改 | Phase 2D |
| `SkillRouter.java` | 不修改 | Phase 2D |
