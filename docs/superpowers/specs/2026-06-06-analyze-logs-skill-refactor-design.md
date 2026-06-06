# Analyze Logs Skill 重构设计

## 背景

现有的 `analyze_logs` 技能过于简陋——仅包含 5 行 SOP、无模式库、无严重性分级、无用户交互门控。需要升级为与 `code_review` 同级别的专业技能。

## 目标

1. 扩展触发关键词覆盖范围（中英文 20+）
2. 建立完整的、带 checklist 的 SOP
3. 内联通用日志模式库（Java/Spring Boot 常见异常）
4. 引入 P0-P3 严重性分级
5. 结构化报告输出模板
6. 用户确认门控（Next Steps）

## 非目标

- 不引入外部参考文件（模式库直接内联在 SKILL.md 中）
- 不修改 Java 技能加载/路由代码
- 不新增 Java Executor 类

## 具体变更

### 1. config.yaml

- 新增 `allowed_tools`: Grep, Glob（用于模式搜索和文件查找）
- 版本号升级到 2.0.0

### 2. SKILL.md

**Frontmatter:**
- 扩展 `triggerKeywords` 到 20+ 项
- 添加 `disabledKeywords`

**Body 结构:**

| 章节 | 内容 |
|------|------|
| Iron Law | 只分析不修改的原则 |
| 什么时候启用 | 触发条件说明 |
| 日志格式识别 | Java/Spring Boot、系统日志、自定义格式特征 |
| 分析工作流 | 6 步 checklist 格式 SOP |
| 通用异常模式库 | OOM、NPE、连接超时、死锁等 10+ 种模式 |
| 严重性分级 | P0-P3 定义 |
| 输出格式 | 结构化 Markdown 报告模板 |
| 确认门控 | 用户下一步操作选择 |
| 依赖的 Tools | Read, Bash, Grep, Glob |

## 关键设计决策

1. **模式库内联而非外置文件** — 保持技能自包含，减少加载复杂性
2. **采用 code_review 同款 checklist 格式** — 风格统一，用户可跟踪进度
3. **不添加 Java Executor** — 纯指令驱动，路由层无需修改
4. **通用模式优先** — 不绑定特定框架，可覆盖 Spring Boot、Netty、Tomcat 等

## 交付物

- `src/main/resources/skills/analyze_logs/SKILL.md`（重写）
- `src/main/resources/skills/analyze_logs/config.yaml`（更新）
