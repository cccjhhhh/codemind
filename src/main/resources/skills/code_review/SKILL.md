---
name: code_review
description: 对代码变更进行专家级审查，发现 SOLID 违规、安全风险并提供可操作改进建议。
triggerKeywords:
  - 审查代码
  - review code
  - 检查代码
  - check code
  - 代码审查
  - code review
  - 审查变更
  - review changes
---

# Code Review Skill

IRON LAW: 这是 review-first workflow。除非用户明确要求实施修改，否则只输出审查报告，不自动修改代码。

## 严重性分级

| Level | Name | Description | Action |
|-------|------|-------------|--------|
| **P0** | Critical | 安全漏洞、数据丢失风险、正确性 bug | 必须阻止合并 |
| **P1** | High | 逻辑错误、重大 SOLID 违规、性能退化 | 应在合并前修复 |
| **P2** | Medium | 代码异味、可维护性问题、轻微 SOLID 违规 | 在此 PR 修复或创建 follow-up |
| **P3** | Low | 风格、命名、轻微建议 | 可选改进 |

## 工作流程

```
Code Review Progress:

- [ ] Step 1: Preflight context
  - [ ] 执行 git status -sb, git diff --stat, git diff 获取变更范围
  - [ ] 必要时使用 grep 查找相关模块和依赖关系
- [ ] Step 2: SOLID + architecture review
  - [ ] 读取 references/solid-checklist.md 作为审查指引
  - [ ] 检查 SRP/OCP/LSP/ISP/DIP 违规
- [ ] Step 3: Security review
  - [ ] 读取 references/security-checklist.md 作为审查指引
  - [ ] 检查常见安全问题（注入、敏感信息泄漏、权限缺失、竞态条件）
- [ ] Step 4: Code quality review
  - [ ] 读取 references/code-quality-checklist.md 作为审查指引
  - [ ] 检查错误处理、性能问题、边界条件
- [ ] Step 5: Output report
  - [ ] 按下方格式直接输出审查结果
- [ ] Step 6: Next steps confirmation
  - [ ] 报告完成后，询问用户下一步操作
```

## 输出格式

直接在对话中输出以下格式的审查报告：

```markdown
## Code Review Summary
Files: X  |  Lines changed: Y  |  Assessment: APPROVE / CHANGES REQUESTED
Issues: P0=X  P1=X  P2=X  P3=X

---

### P0 — Critical

1. **[File.java:42]** 问题标题
   - 问题描述
   - 修复建议

### P1 — High

2. **[File.java:88]** 问题标题
   - 问题描述
   - 修复建议

### P2 — Medium
...

### P3 — Low
...
```

## 确认门控

报告完成后，询问用户下一步操作：

```
下一步:
1. **Fix all** — 实施所有修复
2. **Fix P0/P1 only** — 仅修复关键和高优先问题
3. **Fix specific items** — 告知需要修复的具体问题编号
4. **No changes** — 审查完成，无需修改
```

**重要**: 在用户明确确认前，不要实施任何修改。

## 依赖的 Tools

- Read
- Bash
- Grep
- Write
