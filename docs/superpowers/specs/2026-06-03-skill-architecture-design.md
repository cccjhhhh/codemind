# Skill 系统架构设计（方案 A）

> 日期：2026-06-03
> 状态：待实现

## 问题背景

当前 Skill 设计存在致命缺陷：

- **LLM 无法调用 Skill**：Skill 注册到 SkillRegistry，但 AgentLoop 只用 ToolRegistry
- **Skill 变成死代码**：设计文档说 Skill 是"复杂任务封装"，但 LLM 根本不知道这些 Skill 存在
- **与主流框架不符**：设计文档要求参考 LangChain，但 LangChain 的 Skill 就是 Tool

## 解决方案

**方案 A：Skill 包装成 Tool**

- 每个 Skill 包装成一个 `SkillAsTool`，注册到 ToolRegistry
- LLM 通过 Function Calling 调用 Tool，间接调用 Skill
- SkillRegistry 保留作为内部管理机制

## 架构图

```
┌─────────────────────────────────────────────────────────────┐
│                     改造后架构                                  │
├─────────────────────────────────────────────────────────────┤
│                                                             │
│  AgentLoop                                                  │
│      │                                                      │
│      ▼                                                      │
│  ToolRegistry                                               │
│      │                                                      │
│      ├── git_diff (Tool)                                    │
│      ├── read_file (Tool)                                   │
│      ├── execute_command (Tool)                             │
│      │                                                      │
│      └── code_review (SkillAsTool)  ← Skill 包装成 Tool     │
│              │                                              │
│              ▼                                              │
│          CodeReviewSkill                                    │
│              │                                              │
│              ▼                                              │
│          [git_diff, read_file] 内部调用 Tool                │
│                                                             │
│  SkillRegistry (内部管理，不暴露给 LLM)                       │
│      └── CodeReviewSkill                                    │
│      └── DocGenSkill                                        │
│      └── LogAnalysisSkill                                   │
│                                                             │
└─────────────────────────────────────────────────────────────┘
```

## 核心组件

### 1. SkillAsTool（通用包装器）

```java
/**
 * Skill 包装成 Tool 的适配器
 * 
 * 作用：将 Skill 转换为 LLM 可调用的 Tool
 * 设计：一个 SkillAsTool 类通用包装所有 Skill，无需为每个 Skill 写新包装器
 */
public class SkillAsTool implements Tool {
    
    private final Skill skill;
    
    public SkillAsTool(Skill skill) {
        this.skill = skill;
    }
    
    @Override
    public String getName() {
        return skill.getName();  // 直接用 Skill 的名字，如 "code_review"
    }
    
    @Override
    public String getDescription() {
        return skill.getDescription();
    }
    
    @Override
    public JsonNode getInputSchema() {
        // Skill 共用参数
        return schemaBuilder()
            .optional("skill_param", "string", "Skill 特定参数")
            .build();
    }
    
    @Override
    public ToolResult execute(Map<String, Object> params) {
        // 构建 SkillContext
        SkillContext context = new SkillContext(...);
        
        // 调用 Skill
        SkillResult result = skill.execute(context);
        
        // 转换 SkillResult → ToolResult
        if (result.isSuccess()) {
            return ToolResult.success(result.getOutput());
        } else {
            return ToolResult.failure(result.getError());
        }
    }
}
```

### 2. SkillContext（Skill 执行上下文）

```java
/**
 * Skill 执行所需的上下文信息
 */
public class SkillContext {
    
    private final SessionContext sessionContext;  // 会话上下文
    private final Map<String, Object> parameters;  // Skill 参数
    
    // 内部调用 Tool 的能力
    private final ToolRegistry toolRegistry;
    
    public SkillContext(SessionContext sessionContext, 
                       Map<String, Object> parameters,
                       ToolRegistry toolRegistry) {
        this.sessionContext = sessionContext;
        this.parameters = parameters;
        this.toolRegistry = toolRegistry;
    }
    
    // Skill 可以调用其他 Tool
    public ToolResult callTool(String toolName, Map<String, Object> params) {
        return toolRegistry.execute(toolName, params);
    }
}
```

### 3. SkillRegistry（内部管理）

```java
/**
 * Skill 注册中心（内部使用，不暴露给 LLM）
 * 
 * 职责：
 * - 管理所有 Skill 实例
 * - 提供 Skill 列表给管理员/调试用
 * - Skill 的生命周期管理
 */
public interface SkillRegistry {
    void register(Skill skill);
    void unregister(String name);
    Skill get(String name);
    List<String> getAllNames();
    boolean hasSkill(String name);
}
```

## 注册流程

```java
// AppBinder.java 或 CLI.java

public void setup() {
    // 1. 创建 ToolRegistry
    ToolRegistry toolRegistry = createToolRegistry();
    
    // 2. 创建 SkillRegistry（内部管理）
    SkillRegistry skillRegistry = new SkillRegistryImpl();
    
    // 3. 创建 Skill（依赖 ToolRegistry）
    Skill codeReviewSkill = new CodeReviewSkill(toolRegistry);
    Skill docGenSkill = new DocGenSkill(toolRegistry);
    Skill logAnalysisSkill = new LogAnalysisSkill(toolRegistry);
    
    // 4. 注册到 SkillRegistry（内部管理）
    skillRegistry.register(codeReviewSkill);
    skillRegistry.register(docGenSkill);
    skillRegistry.register(logAnalysisSkill);
    
    // 5. 包装成 Tool，注册到 ToolRegistry（LLM 可调用）
    toolRegistry.register(new SkillAsTool(codeReviewSkill));
    toolRegistry.register(new SkillAsTool(docGenSkill));
    toolRegistry.register(new SkillAsTool(logAnalysisSkill));
    
    // 6. 创建 AgentLoop（只传入 ToolRegistry）
    AgentLoop agentLoop = new AgentLoop(llmClient, toolRegistry, ...);
}
```

## LLM 调用流程

```
用户: "帮我审查代码"

AgentLoop:
1. 构建消息，发送给 LLM
2. LLM 返回 tool_calls: [{"name": "code_review", "arguments": {...}}]
3. AgentLoop 调用 toolRegistry.execute("code_review", {...})
4. SkillAsTool.execute() 被调用
5. SkillAsTool 构建 SkillContext，调用 CodeReviewSkill.execute()
6. CodeReviewSkill 内部调用 git_diff、read_file 等 Tool
7. 返回结果给 LLM
8. LLM 生成最终回复
```

## 新增文件清单

| 文件 | 位置 | 说明 |
|------|------|------|
| SkillAsTool.java | `impl/skill/` | Skill 转 Tool 包装器 |
| SkillContext.java | `api/skill/` | Skill 执行上下文 |
| SkillResult.java | `api/skill/` | Skill 执行结果 |

## 修改文件清单

| 文件 | 修改内容 |
|------|----------|
| CodeReviewSkill.java | 实现 Skill 接口，移除直接注册到 ToolRegistry 逻辑 |
| SkillRegistry.java | 确认接口不变 |
| AppBinder.java | 添加 Skill 注册逻辑 |
| CLI.java | 调用 SkillRegistry 注册流程 |

## 设计原则

| 原则 | 说明 |
|------|------|
| **单一职责** | SkillAsTool 只负责"包装"，不包含业务逻辑 |
| **开闭原则** | 新增 Skill 不需要修改 SkillAsTool |
| **依赖倒置** | SkillAsTool 依赖 Skill 接口，不依赖具体实现 |

## 优点

1. **LLM 可调用**：Skill 通过 Tool 暴露给 LLM
2. **复用包装器**：一个 SkillAsTool 包装所有 Skill
3. **内部复用**：Skill 可调用其他 Tool，实现复杂编排
4. **符合主流**：与 LangChain 做法一致

## 缺点

1. Skill 和 Tool 概念混淆（但这是可以接受的权衡）
2. Skill 调用 Tool 需要通过 SkillContext 间接调用

## 实现顺序

1. 创建 `SkillContext` 接口和实现
2. 创建 `SkillResult` 类
3. 创建 `SkillAsTool` 通用包装器
4. 修改 `CodeReviewSkill` 实现 `Skill` 接口
5. 在 `AppBinder` 中注册 Skill
6. 更新设计文档

---

*文档版本：v1.0*
*创建日期：2026-06-03*
