---
description: 所有压缩必须经过 ContextCompressionOrchestrator 唯一入口。
globs: "**/compression/**"
---
# 压缩管线单入口

## 强制
- 所有压缩/上下文管理操作必须通过 `ContextCompressionOrchestrator` 单一入口
- 执行顺序固定：`L1 → L2 → L3 → (L4 按需)`
- Read 工具结果索引在管线开始时一次扫描，后续阶段复用此索引

## 重叠职责清理
- `SlidingWindowManager`: 只做窗口裁剪（超限时删除最旧消息），**禁止**做工具结果占位
- `TruncationHook`: 只做摘要预览（头800+尾400字符），**禁止**落盘
- `CompactionPipeline.L3`: 唯一的大结果落盘入口

## L4 摘要约束
- ❌ 禁止 `context.clearHistory() + context.addMessage(user(summary))`
- ✅ 必须保留消息结构：system(摘要) + user(原始用户输入) + assistant(最近工具调用)
- 压缩后的消息格式：
  ```
  system("[Previous conversation compressed]")
  system(summary_content)
  user(current_user_input)
  assistant(tool_call) + tool(result)  ← 最近 N 轮完整保留
  ```

## 追溯
- 源于 #arch-review-002: getManagedHistory + TokenBudget + CompactionPipeline 三条路径独立运行
- 源于 #arch-review-003: L4 clearHistory() 丢失所有 role 信息，后续无法做消息级压缩
- 源于 #arch-review-004: TruncationHook 和 L3 双重落盘
