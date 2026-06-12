# AgentLoop 状态转移图

## 状态枚举
```
THINK   ← LLM 推理 + 工具决策 → ACT / COMPLETE
ACT     ← 执行 THINK 选定的工具 → THINK
COMPLETE    ← LLM 回复完成，Agent 结束
ERROR       ← 不可恢复错误，Agent 终止
MAX_ITERATIONS  ← 达到迭代上限，Agent 终止

# 上下文恢复
RECOVERY_COMPACT ← LLM 返回空结果/ContextOverflow → 触发 L4 摘要

# max_tokens 截断
RECOVERY_ESCALATE ← max_tokens 截断，升级到更大值后重试
CONTINUATION     ← max_tokens 已达最高级，追加续写提示

# 网络错误
RETRY_BACKOFF    ← 429/529/5xx → 指数退避后重试

# 工具侧恢复
RECOVERY_FAILOVER ← ACT 连续 5 轮工具全失败 → 切换 fallback 模型
LOOP_DETECTED    ← THINK 循环检测触发 → 压缩打断

# Token 预算
TOKEN_BUDGET_CONTINUE ← 预算紧张但压缩失败 → 精简回复提示
```

## 状态转移规则
```
THINK ──── LLM 正常返回 + 有工具 ──→ ACT ──── THINK
THINK ──── LLM 正常返回 + 无工具 ──→ COMPLETE
                                       或 THINK (由 StopHook 判定)
THINK ──── LLM 空结果      ──→ RECOVERY_COMPACT ──→ THINK
                                    └─ 再次空结果  ──→ ERROR
THINK ──── max_tokens截断  ──→ RECOVERY_ESCALATE ──→ THINK
                                    └─ 已最高级    ──→ CONTINUATION ──→ THINK
                                                          └─ 已达上限 ──→ ERROR
THINK ──── 循环检测触发    ──→ LOOP_DETECTED     ──→ THINK
ACT  ──── 全部失败 ×5      ──→ RECOVERY_FAILOVER ──→ THINK
ACT  ──── 部分成功        ──→ THINK

MAX_ITERATIONS ──→ 终止
ERROR           ──→ 终止
USER_INTERRUPT  ──→ 终止
```
注：LLM 超时/429 由 runSingleTurn 内部重试处理，不触发状态转移。

## Handler 对应关系
```
ContinueReason      → StateHandler     → 核心委托对象
THINK               → ThinkHandler     → LLMClient + CompactionPipeline
ACT                 → ActHandler       → ToolRegistry + ToolRetryStrategy
RECOVERY_COMPACT    → CompactHandler   → ContextCompressionOrchestrator
RECOVERY_ESCALATE   → EscalateHandler  → MaxTokensStrategy
CONTINUATION        → ContinuationHandler → ContinuationStrategy
RETRY_BACKOFF       → RetryHandler     → (指数退避)
RECOVERY_FAILOVER   → FailoverHandler  → FallbackStrategy
LOOP_DETECTED       → LoopBreakHandler → LoopDetector + Compressor
TOKEN_BUDGET_CONTINUE → BudgetHandler  → TokenBudgetService
```
