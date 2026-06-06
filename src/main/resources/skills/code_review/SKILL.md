---
name: code_review
description: >
  对代码变更进行专家级审查，发现 SOLID 违规、安全风险并提供可操作改进建议。
  当用户要求审查代码、检查变更、或在 git commit 前自动触发。
  Triggers: 审查代码, review code, 检查代码, check code, 代码审查, code review,
  review my changes, 代码审查, review changes, 审核代码
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
  - [ ] 1.1 执行 git status -sb, git diff --stat, git diff 获取变更范围
  - [ ] 1.2 必要时使用 grep 查找相关模块和依赖关系
  - [ ] 1.3 识别入口点、关键路径（auth、payments、数据写入、网络）
- [ ] Step 2: SOLID + architecture smells
  - [ ] 2.1 加载 references/solid-checklist.md 获取具体检查提示
  - [ ] 2.2 检查 SRP/OCP/LSP/ISP/DIP 违规
  - [ ] 2.3 提出重构建议时解释改进原因
- [ ] Step 3: Removal candidates
  - [ ] 3.1 加载 references/removal-plan.md
  - [ ] 3.2 区分 safe delete now vs defer with plan
- [ ] Step 4: Security scan
  - [ ] 4.1 加载 references/security-checklist.md
  - [ ] 4.2 检查注入/XSS/SSRF/AuthZ/AuthN/竞态条件/Secrets
- [ ] Step 5: Code quality scan
  - [ ] 5.1 加载 references/code-quality-checklist.md
  - [ ] 5.2 检查错误处理/性能/边界条件
- [ ] Step 6: Output format
  - [ ] 6.1 按模板格式输出 Markdown 报告
  - [ ] 6.2 包含 P0-P3 分级和文件行号
- [ ] Step 7: Next steps confirmation
  - [ ] 7.1 询问用户选择下一步操作
```

## 边缘情况

- **无变更**: 如果 `git diff` 为空，告知用户并询问是否审查 staged changes 或特定 commit 范围
- **大量变更 (>500 行)**: 按模块/功能区域分批审查
- **混合变更**: 按逻辑功能分组，而非文件顺序

## 输出格式

```markdown
## Code Review Summary

**Files reviewed**: X files, Y lines changed
**Overall assessment**: [APPROVE / REQUEST_CHANGES / COMMENT]

---

## Findings

### P0 - Critical
(none or list)

### P1 - High
1. **[file:line]** Brief title
   - Description of issue
   - Suggested fix

### P2 - Medium
2. (continue numbering across sections)
   - ...

### P3 - Low
...

---

## Removal/Iteration Plan
(if applicable)

## Additional Suggestions
(optional improvements, not blocking)
```

**行内注释格式**:
```
::code-comment{file="path/to/file.ts" line="42" severity="P1"}
Description of the issue and suggested fix.
::
```

## 确认门控

审查完成后必须询问用户下一步操作：

```markdown
## Next Steps

I found X issues (P0: _, P1: _, P2: _, P3: _).

**How would you like to proceed?**

1. **Fix all** - I'll implement all suggested fixes
2. **Fix P0/P1 only** - Address critical and high priority issues
3. **Fix specific items** - Tell me which issues to fix
4. **No changes** - Review complete, no implementation needed

Please choose an option or provide specific instructions.
```

**重要**: 在用户明确确认前，不要实施任何修改。

## 依赖的 Tools

- Read
- Bash
- Grep
