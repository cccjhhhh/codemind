---
name: generate_docs
description: 为代码生成文档，包括 API 文档、README 等
triggerKeywords:
  - 生成文档
  - generate docs
  - 写文档
  - 文档生成
  - doc gen
  - 生成 readme
disabledKeywords:
  - 跳过文档
  - skip docs
---

# Generate Docs Skill

## 什么时候启用

当用户要求生成文档、写 README、或为代码添加注释。

## 执行流程（SOP）

1. 扫描指定目录的源文件
2. 解析类、方法、字段的结构
3. 提取现有注释
4. 根据代码逻辑生成文档
5. 格式化输出（Markdown）

## 依赖的 Tools

- read_file
- write_file
- execute_command
