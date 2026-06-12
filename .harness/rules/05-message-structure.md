---
description: 消息领域模型的 role 结构和配对关系是核心不变式，禁止破坏。
globs: "**/domain/message/**"
---
# 消息结构完整性

## 强制
- Message 的 role（SYSTEM/USER/ASSISTANT/TOOL）是核心不变式，禁止丢失
- ASSISTANT 与 TOOL 的配对关系（通过 toolCallId）任何时候都不能断裂
- 任何修改消息列表的操作必须保持：删除 ASSISTANT 时连带删除其 TOOL 响应

## 不变式校验
```java
// 发送给 LLM 之前的消息列表必须满足:
// 1. role 序列合法: SYSTEM?(USER|ASSISTANT|TOOL)*
// 2. TOOL 消息必须有匹配的 ASSISTANT(tool_calls) 在其之前
// 3. 不可有孤立的 TOOL 消息
```

## 禁止
- ❌ 将结构化消息替换为纯文本摘要（如 `Message.user("[Compacted]\n\n" + summary)`）
- ❌ 单独删除 ASSISTANT 消息而保留其 TOOL 结果
- ❌ 在 `SessionContext.addMessage()` 中隐式删除配对消息而不通知调用方

## 追溯
- 源于 #arch-review-005: L4 摘要后 clearHistory() + addMessage(user(summary)) 丢失所有 role 信息
