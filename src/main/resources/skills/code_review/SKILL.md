---
name: code_review
description: 对代码变更进行审查，发现潜在问题并提供改进建议
triggerKeywords:
  - 审查代码
  - review code
  - 检查代码
  - check code
  - 代码审查
  - code review
disabledKeywords:
  - 忽略审查
  - skip review
  - 跳过审查
---

# Code Review Skill

## 什么时候启用

当用户要求审查代码、检查变更、或在 git commit 前自动触发。

## 禁用场景

- 用户明确说"忽略审查"或"skip review"
- 没有代码变更（git diff 为空）

## 执行流程（SOP）

1. 执行 `git diff --cached` 获取暂存区变更
2. 如果暂存区为空，执行 `git diff` 获取工作区变更
3. 解析变更文件列表
4. 构建依赖图（解析 import 语句）
5. 查找受影响文件（BFS 遍历，最多 2 跳）
6. 读取变更文件和受影响文件内容
7. 计算风险评分
8. 生成审查报告

## 输出格式

返回 JSON 结构化数据，包含：
- `status`: 状态（success/no_changes/error）
- `changes`: 变更文件信息
- `affected`: 受影响文件信息
- `contents`: 文件内容预览
- `highRiskFiles`: 高风险文件列表
- `stats`: 统计信息

## 异常处理

- 如果 git 不可用：返回错误 "git 不可用，请确保在 git 仓库中执行"
- 如果依赖图构建失败：跳过依赖分析，只分析变更文件
- 如果文件读取失败：记录错误但继续分析其他文件

## 依赖的 Tools

- execute_command
- read_file
