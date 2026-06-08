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
  - review changes
  - 审核代码
  - 审查变更
  - review my changes
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
  - [ ] 检查 SRP/OCP/LSP/ISP/DIP 违规
- [ ] Step 3: Security review
  - [ ] 检查常见安全问题（注入、敏感信息泄漏、权限缺失）
- [ ] Step 4: Code quality review
  - [ ] 检查错误处理、性能问题、边界条件
- [ ] Step 5: Output format
  - [ ] 列出发现摘要后，询问用户选择输出格式
- [ ] Step 6: Generate report
  - [ ] 按用户选择的格式生成报告（Markdown 直接输出 / HTML 写文件 / 不生成）
- [ ] Step 7: Next steps confirmation
  - [ ] 报告完成后，询问用户下一步操作
```

## 输出格式

审查完成后，**先列出发现摘要，再询问用户选择格式**：

| 选项 | 命令 | 说明 |
|------|------|------|
| Markdown | `m` 或 `md` | 使用 Write tool 生成 Markdown 报告（默认） |
| HTML | `h` 或 `html` | 使用 Write tool 生成 `code-review-report.html` 文件 |
| None | `n` 或 `none` | 只显示总结，不生成详细报告 |

### 选项 A — Markdown 报告（默认）

**必须使用 Write tool** 生成 Markdown 格式报告：

```markdown
## Code Review Summary
...
### P0 - Critical
1. **[File.java:42]** 问题标题
   - 问题描述
   - 修复建议
```

### 选项 B — HTML 报告

**必须使用 Write tool** 生成 `code-review-report.html` 文件到当前目录。**禁止**直接在对话中输出 HTML 内容。

**执行步骤**:
1. 使用 `Write` tool 将完整 HTML 内容写入 `code-review-report.html`
2. 文件路径必须为当前工作目录下的绝对路径: `D:\agent_learning\codemind\code-review-report.html`
3. 写入完成后输出确认信息：`✅ HTML 报告已生成: D:\agent_learning\codemind\code-review-report.html`

模板如下（填写时替换占位内容）：

```html
<!DOCTYPE html>
<html lang="zh-CN">
<head>
<meta charset="UTF-8">
<title>Code Review Report</title>
<style>
  * { margin: 0; padding: 0; box-sizing: border-box; }
  body { font-family: -apple-system, BlinkMacSystemFont, 'Segoe UI', Roboto, sans-serif; background: #f5f5f5; color: #24292f; line-height: 1.6; padding: 24px; }
  .container { max-width: 960px; margin: 0 auto; }
  .header { background: linear-gradient(135deg, #24292f, #2b3137); color: #fff; padding: 32px; border-radius: 12px; margin-bottom: 24px; }
  .header h1 { font-size: 24px; margin-bottom: 8px; }
  .header .meta { color: #8b949e; font-size: 14px; }
  .header .meta span { margin-right: 20px; }
  .badge-approve { display: inline-block; background: #2da44e; color: #fff; padding: 4px 14px; border-radius: 20px; font-weight: 600; font-size: 14px; }
  .badge-changes { display: inline-block; background: #d93f0b; color: #fff; padding: 4px 14px; border-radius: 20px; font-weight: 600; font-size: 14px; }
  .badge-comment { display: inline-block; background: #9a6700; color: #fff; padding: 4px 14px; border-radius: 20px; font-weight: 600; font-size: 14px; }
  .summary { display: grid; grid-template-columns: repeat(4, 1fr); gap: 12px; margin-bottom: 24px; }
  .summary-card { background: #fff; border-radius: 8px; padding: 16px; text-align: center; box-shadow: 0 1px 3px rgba(0,0,0,0.08); }
  .summary-card .count { font-size: 32px; font-weight: 700; }
  .summary-card .label { font-size: 13px; color: #57606a; margin-top: 4px; }
  .card-p0 .count { color: #cf222e; }
  .card-p1 .count { color: #d93f0b; }
  .card-p2 .count { color: #9a6700; }
  .card-p3 .count { color: #6e7781; }
  .section { background: #fff; border-radius: 8px; margin-bottom: 16px; box-shadow: 0 1px 3px rgba(0,0,0,0.08); overflow: hidden; }
  .section-header { padding: 14px 20px; font-weight: 600; font-size: 16px; cursor: pointer; display: flex; align-items: center; justify-content: space-between; user-select: none; }
  .section-header:hover { opacity: 0.85; }
  .section-header .toggle { font-size: 12px; color: #57606a; }
  .section-body { padding: 0 20px 16px; }
  .section-body.collapsed { display: none; }
  .sh-P0 { background: #ffeef0; border-bottom: 1px solid #ffdce0; }
  .sh-P1 { background: #fff0e8; border-bottom: 1px solid #ffd4c2; }
  .sh-P2 { background: #fffaef; border-bottom: 1px solid #ffd9a8; }
  .sh-P3 { background: #f6f8fa; border-bottom: 1px solid #d0d7de; }
  .issue { padding: 12px 0; border-bottom: 1px solid #eee; }
  .issue:last-child { border-bottom: none; }
  .issue-title { font-weight: 600; font-size: 15px; margin-bottom: 6px; }
  .issue-title .location { color: #0969da; font-weight: 500; }
  .issue-desc { color: #24292f; font-size: 14px; margin-bottom: 6px; }
  .issue-fix { background: #f0fff4; border-left: 3px solid #2da44e; padding: 8px 12px; border-radius: 4px; font-size: 14px; color: #1a5e2a; margin-top: 6px; }
  .issue-fix::before { content: "建议: "; font-weight: 600; }
  .empty-state { color: #57606a; font-style: italic; padding: 8px 0; font-size: 14px; }
  .footer { text-align: center; color: #57606a; font-size: 12px; padding: 24px 0; }
  @media (max-width: 640px) { .summary { grid-template-columns: repeat(2, 1fr); } body { padding: 12px; } }
</style>
</head>
<body>
<div class="container">
  <div class="header">
    <h1>Code Review Report</h1>
    <div class="meta">
      <span>📂 X files reviewed</span>
      <span>📝 Y lines changed</span>
      <span>📅 YYYY-MM-DD HH:mm</span>
    </div>
    <div style="margin-top: 12px;">
      <span class="badge-approve">APPROVE</span>
    </div>
  </div>
  <div class="summary">
    <div class="summary-card card-p0"><div class="count">0</div><div class="label">P0 Critical</div></div>
    <div class="summary-card card-p1"><div class="count">0</div><div class="label">P1 High</div></div>
    <div class="summary-card card-p2"><div class="count">0</div><div class="label">P2 Medium</div></div>
    <div class="summary-card card-p3"><div class="count">0</div><div class="label">P3 Low</div></div>
  </div>
  <div class="section">
    <div class="section-header sh-P0" onclick="this.nextElementSibling.classList.toggle('collapsed')">
      <span>🔴 P0 — Critical</span>
      <span class="toggle">▼</span>
    </div>
    <div class="section-body"><div class="empty-state">没有发现 Critical 级别问题</div></div>
  </div>
  <div class="section">
    <div class="section-header sh-P1" onclick="this.nextElementSibling.classList.toggle('collapsed')">
      <span>🟠 P1 — High</span>
      <span class="toggle">▼</span>
    </div>
    <div class="section-body">
      <div class="issue">
        <div class="issue-title"><span class="location">File.java:42</span> 问题标题</div>
        <div class="issue-desc">问题描述文字</div>
        <div class="issue-fix">修复建议</div>
      </div>
    </div>
  </div>
  <div class="section">
    <div class="section-header sh-P2" onclick="this.nextElementSibling.classList.toggle('collapsed')">
      <span>🟡 P2 — Medium</span>
      <span class="toggle">▼</span>
    </div>
    <div class="section-body"><div class="empty-state">没有发现 Medium 级别问题</div></div>
  </div>
  <div class="section">
    <div class="section-header sh-P3" onclick="this.nextElementSibling.classList.toggle('collapsed')">
      <span>⚪ P3 — Low</span>
      <span class="toggle">▼</span>
    </div>
    <div class="section-body"><div class="empty-state">没有发现 Low 级别问题</div></div>
  </div>
  <div class="footer">Generated by CodeMind Code Review</div>
</div>
</body>
</html>
```

### 选项 C — 不生成报告

仅输出简洁总结：

```
## Code Review Summary
Files: X  |  Lines changed: Y  |  Assessment: APPROVE
Issues: P0=X  P1=X  P2=X  P3=X
```

## 格式选择提问

审查分析完成后，严格按以下格式询问：

```
共发现 X 个问题 (P0: _, P1: _, P2: _, P3: _)

请选择报告输出格式:
  [m] Markdown — 保存为 Markdown 报告（默认）
  [h] HTML — 保存为 code-review-report.html
  [n] None — 不生成详细报告，仅以上总结
```

根据用户选择执行对应的输出。

**快速路径（优先判断）**:
- 若用户原始指令已明确指定格式（包含 `html`/`HTML`/`生成 html 报告`/`输出 markdown` 等关键词），**跳过询问，直接进入对应输出流程**
- 若用户说"直接生成"/"不要问"等，明确跳过 Step 5
- 否则才执行上述询问

**重要执行规则**:
- `m` — 直接在对话中输出 Markdown 内容
- `h` — **必须调用 Write tool** 写入文件，禁止在对话中输出 HTML
- `n` — 仅输出简洁总结，不生成文件

## 关键执行约束（防卡死）

**绝对禁止**以下行为：
1. 反复输出"现在开始生成报告""即将调用 Write tool"等声明性文字而不实际调用工具 —— **声明不等于执行**
2. 在描述完发现后就停止 turn —— **必须用 tool call 结束本轮**
3. 写 HTML 时将内容截断后说"剩余部分省略" —— **必须一次性写入完整内容**

**自检规则**（每轮 turn 结束前）：
- 如果你本轮的回复文本包含"生成报告""写文件""Write tool"等关键词，但 `tool_calls` 字段为空 → **你的回复是失败的**，下一轮必须立即调用 Write tool
- 如果你说"我先 X 然后 Y"，但 X 永远不结束 → 立刻停止叙述，**直接调用工具执行**

## 确认门控

报告生成后，必须询问用户下一步操作：

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
