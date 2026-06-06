---
name: analyze_logs
description: 分析日志文件，识别异常模式并生成报告
triggerKeywords:
  - 分析日志
  - analyze logs
  - 查看日志
  - 日志分析
  - parse logs
  - 检查日志
disabledKeywords:
  - 跳过日志
  - skip logs
---

# Analyze Logs Skill

## 什么时候启用

当用户要求分析日志、查看日志、或排查问题时。

## 执行流程（SOP）

1. 读取日志文件
2. 解析日志格式（时间戳、级别、消息）
3. 按级别分类（ERROR、WARN、INFO、DEBUG）
4. 提取异常堆栈
5. 统计频率
6. 生成分析报告

## 输出格式

返回 JSON 结构化数据，包含：
- `summary`: 摘要统计
- `errors`: 错误列表
- `warnings`: 警告列表
- `patterns`: 发现的模式

## 依赖的 Tools

- Read
- Bash
