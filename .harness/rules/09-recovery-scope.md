---
description: RecoveryManager 只做恢复策略，循环检测必须独立为 LoopDetector。
globs: "**/recovery/**"
---
# 恢复策略与检测算法分离

## 强制
- `RecoveryManager` 拆分为三个独立策略类：
  - `MaxTokensStrategy` — max_tokens 三级升级
  - `ContinuationStrategy` — 续写次数追踪
  - `FallbackStrategy` — 529 追踪 + fallback 切换
- 循环检测必须独立为 `LoopDetector`：
  - 放在 `core/service/detection/` 包下
  - 只负责检测算法，不关心 AgentLoop 状态转移

## LoopDetector 设计约束
- 输入：工具调用流（toolName + argsDigest）
- 输出：`LoopDetectionResult( detected: boolean, confidence: double, pattern: String )`
- 检测策略必须可插拔（策略模式），当前实现"连续重复检测"为一种策略
- 缓冲区大小、阈值、冷却步数必须可配置

## 追溯
- 源于 #arch-review-009: RecoveryManager 同时负责 max_tokens/续写/529/循环检测/compact标记 6 项无关职责
