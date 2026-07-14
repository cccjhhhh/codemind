# CodeMind 上下文压缩管线改造方案

> 基于 Claude Code 压缩机制的深度分析，提出 CodeMind 上下文压缩管线的改造方案。
> 目标：从"机械裁剪为主"转变为"简单规则 + 缓存利用 + 摘要保底"的工程化方案。

---

## 一、问题分析

### 1.1 当前设计的核心缺陷

| 缺陷 | 当前做法 | 问题 |
|------|---------|------|
| **L2 截断过于暴力** | 一刀切砍 15 轮 | 不考虑内容重要性，可能丢失关键信息 |
| **L3 缩写效果有限** | 只缩写非 Read/Grep（17.4%） | Read 占 82.6%，L3 几乎没用 |
| **L3 信息丢失** | 替换为占位符 | 丢失所有内容，无法恢复 |
| **L4 触发阈值过高** | 95% 才触发 | 几乎不会触发，LLM 摘要能力浪费 |
| **触发条件不精确** | 基于轮次数 | 无法精确控制压缩时机 |

### 1.2 数据支撑

基于 10 个真实会话的数据分析：

| 指标 | 数值 | 说明 |
|------|------|------|
| **Read 工具占比** | 82.6% | 398/482 次工具调用 |
| **Bash 工具占比** | 9.5% | 46/482 次 |
| **Glob 工具占比** | 5.6% | 27/482 次 |
| **最大会话 Token** | 129K | 71 步 ReAct |
| **200K 窗口占比** | 64.5% | 最大会话 |
| **L4 实际触发** | 0 次 | 最高 78.2% < 95% |
| **Token 计数服务** | 已有 | `JTokkitTokenCountService`（tiktoken Java 端口） |

### 1.3 根本原因

```
当前设计：
  L2/L3 机械裁剪为主 → L4 摘要为辅（几乎不用）

问题：
  1. L2 一刀切砍 15 轮，不考虑内容重要性
  2. L3 只处理 17.4% 的工具结果，效果有限
  3. L4 阈值 95% 太高，几乎不会触发
  4. 没有利用 LRU 缓存机制（已有 15 个文件的缓存）
  5. 触发条件基于轮次数，不够精确（应该基于 token 计数）
```

---

## 二、压缩管线总结

### 2.1 执行顺序（按 token 使用率）

| 层级 | 名称 | 触发条件 | 作用 |
|------|------|----------|------|
| **L1** | SpillCompactor | >50K tokens | 溢出大文件到磁盘 |
| **L2** | SnipCompactor | >60% (120K) | 剪切旧轮次 |
| **L3** | MicroCompact | >70% (140K) | 清理旧工具结果 |
| **L4** | SummaryCompactor | >90% (180K) | LLM摘要（紧急） |

### 2.2 各层详细设计

**L1: Spill (>50K tokens)**
- 溢出大文件结果（>20K each）到磁盘
- 替换为磁盘引用
- 复用现有 DiskSpiller + LRU cache（30→15）

**L2: Snip (>60% = 120K tokens)**
- Token预算剪切（~20K tokens）
- 保留头部3轮（system+bootstrap+plan+feedback）
- 边界保护：不在 think-act-observe 之间切割
- 保留文件变更历史（edited_files）

**L3: Micro-Compact (>70% = 140K tokens)**
- 清理旧工具结果（不是全部，只清理旧的）
- 保留最近N个工具结果（默认5个）
- 替换为占位符："[Tool result deleted to save context space]"
- 保留 tool_use（用户侧），删除 tool_result 内容
- 只清理可压缩工具（read, grep, bash等）

**L4: Summary (>90% = 180K tokens)**
- LLM 摘要对话
- 注入5个关键文件
- 紧急兜底，不是渐进式

### 2.3 关键设计决策

| 决策 | 选择 | 理由 |
|------|------|------|
| 触发机制 | Token计数 | 复用 JTokkitTokenCountService，精确反映上下文压力 |
| L4触发点 | 90% (180K) | 紧急兜底，不是渐进式摘要 |
| 轮次压缩 | 删除 | 太粗糙，20轮和40轮差距大 |
| 轮次熔断 | 保留 | MAX_ITERATIONS=30，循环检测不变 |
| 混合方式 | 不混合 | 绝对不能采取混合，全是坑 |
| LRU缓存 | 30→15 | 与L4注入文件数一致 |

### 2.4 消息格式

- CodeMind：每个工具调用单独的 TOOL 消息（OpenAI规范）
- 每个工具调用 = 2条消息（user tool_use + assistant tool_result）
- 这就是为什么消息数比 Claude Code 多

---

## 三、改进目标

### 3.1 设计原则

1. **从轻到重**：能不压就不压，必须压的时候从最轻的手段开始
2. **简单可靠**：基于简单规则而不是复杂评分，借鉴 Claude Code 的实践
3. **缓存利用**：充分利用已有的 LRU 缓存机制
4. **保底兜底**：L4 作为紧急逃生阀，但阈值要合理
5. **统一触发**：所有压缩层都基于 token 计数，复用已有的 `JTokkitTokenCountService`

### 3.2 改进目标

| 目标 | 当前 | 改进后 |
|------|------|--------|
| **L1 Spill** | 保持不变 | 保持不变（大结果落盘） |
| **L2 Snip** | 一刀切 15 轮 | L2 Snip：保留前 3 轮 + 最后 ~20K tokens（token 使用率 > 60%） |
| **L3 Micro** | 只处理 17.4%（几乎无效） | L3 Micro：清理旧工具结果，保留最近5个（token 使用率 > 70%） |
| **L4 Summary** | 95%（几乎不用） | L4 Summary：token 使用率 > 90%（紧急逃生阀） |
| **触发方式** | 轮次数 | token 计数（复用已有 `JTokkitTokenCountService`） |
| **信息保留** | 占位符替换 | 保留 tool_use，删除 tool_result 内容 |
| **LRU 缓存** | 30 个文件 | 15 个文件（与 L4 注入文件数一致） |

---

## 四、技术方案：借鉴 Claude Code 的 5 层压缩金字塔

### 4.1 Claude Code 的压缩机制

Claude Code 使用 5 层从轻到重的压缩金字塔：

```
第 1 层：大结果存磁盘（零 API 开销）
  - 工具结果 >50KB → 落盘 + 2KB 预览
  - 完整内容没丢，模型需要时可以再次 Read

第 2 层：Snip 砍掉远古消息（轻量）
  - 删除最旧的探索性提问
  - 插入边界标记

第 3 层：Micro-Compact 时间衰减（轻量）
  - 距离上次 API 调用超过 60 分钟时触发
  - 清空所有「可重新获取」的工具结果，只保留最近 5 个

第 4 层：Context Collapse 读时投影（中等）
  - 上下文达到 90% 时触发
  - 不修改原始消息，只在调用 API 时动态计算压缩视图

第 5 层：Auto-Compact 全量摘要（最重）
  - 上下文逼近窗口上限时触发（留出约 33K 缓冲）
  - 全量重写对话，配套文件恢复、缓存清理
```

### 4.1.1 生产实践对比

基于对多个编码助手的调研，生产系统普遍采用**简单规则**而不是复杂评分：

| 系统 | L1 策略 | L2 策略 | L4 策略 | 特点 |
|------|---------|---------|---------|------|
| **Claude Code** | MicroCompaction：清空旧工具结果，保留最近 5 个 | AutoCompaction：LLM 摘要（83.5%） | Reactive：紧急删除（88.5%） | 简单规则，无评分 |
| **OpenCode** | Prune：标记旧工具结果为 `[Old tool result content cleared]` | 延迟压缩：写 CompactionPart 标记 | 无 | 延迟处理，标记机制 |
| **Pi** | `agent-message` 模式：agent 完成时批量清理 | `agentic-auto`：模型调用 `context_prune` 工具 | `on-demand`：手动触发 | 多种模式可选 |
| **SWE-Pruner** | 神经网络行级剪枝：保留语义块 + 结构依赖 | 无 | 无 | 学术研究，未生产化 |

**关键发现**：

1. **简单规则是生产主流**：Claude Code、OpenCode 都用简单规则，而不是评分系统
2. **评分系统是研究方向**：SWE-Pruner、LaMR 等学术论文用评分，但生产系统很少用
3. **批量大小**：大多数系统在一次 pass 中处理，而不是逐条处理
4. **恢复机制**：Claude Code 在压缩后重新注入最近读取的文件（与我们的 LRU 缓存协作一致）
5. **触发条件**：生产系统普遍基于 token 计数，而不是轮次数

**结论**：我们的设计（简单规则 + 清空工具结果 + LRU 缓存协作 + token 计数触发）与生产实践高度一致。

### 4.2 CodeMind 的改造方案

基于 Claude Code 的设计，CodeMind 的改造方案如下：

```
┌─────────────────────────────────────────────────────────────────┐
│                    CodeMind 新压缩管线                            │
├─────────────────────────────────────────────────────────────────┤
│                                                                  │
│  日常执行：                                                      │
│  ┌─────────────────────────────────────────────────────────────┐│
│  │  每次 Read/Grep 执行后：                                    ││
│  │  - 结果缓存到 LRU（15 个文件）                              ││
│  │  - 缓存持续更新                                            ││
│  └─────────────────────────────────────────────────────────────┘│
│                          ↓                                      │
│  压缩触发：基于 token 计数（复用 JTokkitTokenCountService）      │
│  ┌─────────────────────────────────────────────────────────────┐│
│  │  token 使用率    动作                                        ││
│  │  < 60%          不压缩                                      ││
│  │  60%-70%        L2 Snip（截断旧轮次）                       ││
│  │  70%-90%        L3 Micro（清空旧工具结果）                  ││
│  │  > 90%          L4 Summary（LLM 摘要保底）                  ││
│  └─────────────────────────────────────────────────────────────┘│
│                          ↓                                      │
│  ┌─────────────────────────────────────────────────────────────┐│
│  │  第 1 层：L1 Spill（保持不变）                              ││
│  │  - 工具结果 >50K 字符 → 落盘 + 2KB 预览                    ││
│  └─────────────────────────────────────────────────────────────┘│
│                          ↓                                      │
│  ┌─────────────────────────────────────────────────────────────┐│
│  │  第 2 层：L2 Snip（截断旧轮次）                             ││
│  │  - 保留前 3 轮 + 最后 ~20K tokens                         ││
│  │  - 中间全部删除                                            ││
│  │  - 边界保护：tool_use/tool_result 不分离                   ││
│  │  - 触发：token 使用率 > 60%                                ││
│  └─────────────────────────────────────────────────────────────┘│
│                          ↓                                      │
│  ┌─────────────────────────────────────────────────────────────┐│
│  │  第 3 层：L3 Micro（清理旧工具结果）                        ││
│  │  - 清理旧工具结果（不是全部，只清理旧的）                    ││
│  │  - 保留最近 5 个工具结果                                    ││
│  │  - 完整内容在缓存中，需要时可以恢复                         ││
│  │  - 触发：token 使用率 > 70%                                ││
│  └─────────────────────────────────────────────────────────────┘│
│                          ↓                                      │
│  ┌─────────────────────────────────────────────────────────────┐│
│  │  第 4 层：L4 Summary（LLM 摘要保底）                        ││
│  │  - 全量重写对话（9 段式结构化摘要）                         ││
│  │  - 注入缓存的文件内容（最近 5 个文件）                     ││
│  │  - 触发：token 使用率 > 90%（紧急逃生阀）                  ││
│  └─────────────────────────────────────────────────────────────┘│
│                                                                  │
└─────────────────────────────────────────────────────────────────┘
```

---

## 五、详细设计

### 5.1 命名规范

压缩管线按**执行顺序**命名：

| 顺序 | 原名 | 新名 | 职责 | 触发条件 |
|------|------|------|------|---------|
| 1 | L3SpillCompactor | **L1SpillCompactor** | 大结果存磁盘（>50K） | 工具结果 >50K 字符 |
| 2 | L1SnipCompactor | **L2SnipCompactor** | 截断旧轮次 | token 使用率 > 60% |
| 3 | L2MicroCompactor | **L3MicroCompactor** | 清空旧工具结果 | token 使用率 > 70% |
| 4 | L4SummaryCompactor | **L4SummaryCompactor** | LLM 摘要保底 | token 使用率 > 90% |

**执行顺序**：
```
1. L1: Spill - 大结果存磁盘（>50K）
2. L2: Snip - 截断旧轮次（先删除整个轮次）
3. L3: Micro - 清空旧工具结果（再清空剩余的工具结果）
4. L4: Summary - LLM 摘要保底
```

**为什么这个顺序**：
1. **先截断旧轮次**：删除整个轮次，包括其中的工具结果，减少消息数量
2. **再清空旧工具结果**：只需要处理剩余轮次中的工具结果，更高效
3. **触发条件递增**：60% → 70% → 90%，从轻到重

**优点**：
1. 按执行顺序命名，更清晰
2. 避免混淆（原 L1 和新 L2 是不同的）
3. 便于理解和维护
4. 触发条件基于 token 计数，更精确

---

### 5.2 第 1 层：L1SpillCompactor（大结果存磁盘）

#### 5.2.1 设计目标

- 工具结果 >50K tokens → 落盘 + 2KB 预览
- 完整内容没丢，模型需要时可以再次 Read
- 保持不变

#### 5.2.2 当前实现（保持不变）

当前 L3 实现已经很好，无需修改。

---

### 5.3 第 2 层：L2SnipCompactor（基于 token 计数的智能截断）

#### 5.3.1 设计目标

基于 Claude Code 的 History Snip 实现，采用简单规则而不是复杂评分：

- **保留前 3 轮**：初始上下文（用户意图、系统设置）
- **保留最后 ~20K tokens**：当前工作（最近的对话）
- **中间全部删除**：不考虑内容重要性
- **边界保护**：确保 `tool_use` 和 `tool_result` 不分离
- **触发条件**：基于 token 计数，而不是轮次数
- **注意**：L2 保留的是 HEAD（前3轮），L4 保留的是 TAIL（最后3轮），两者不同

#### 5.3.2 为什么用简单规则而不是评分系统

| 维度 | 评分系统 | 简单规则（Claude Code） |
|------|---------|------------------------|
| **复杂度** | 高（需要评分逻辑） | 低（简单规则） |
| **可预测性** | 低（用户不知道什么会被删） | 高（用户可以预期） |
| **性能** | 差（需要计算分数） | 好（O(1) 判断） |
| **可靠性** | 可能误判 | 确定性行为 |
| **边界保护** | 需要额外实现 | 内置 |

**结论**：Claude Code 选择了简单规则，因为：
1. 简单可靠，不容易出错
2. 用户可以预期什么会被删除
3. 性能好，不需要计算重要性分数
4. 边界保护内置

#### 5.3.3 实现方案

```java
package com.codemind.context;

import com.codemind.llm.Message;
import com.codemind.llm.ToolCall;
import com.codemind.session.JTokkitTokenCountService;
import com.codemind.session.TokenCountService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;

/**
 * L2 截断器：基于 token 计数的智能截断。
 *
 * 保留策略（基于 token 预算，不是固定轮数）：
 * - 保留前 KEEP_HEAD_ROUNDS 轮（初始上下文）
 * - 保留最后约 TAIL_TOKEN_BUDGET tokens（当前工作）
 * - 至少保留 MIN_TAIL_MESSAGES 条消息
 * - 中间全部删除
 *
 * 触发条件：
 * - 基于 token 计数，而不是轮次数
 * - 复用已有的 JTokkitTokenCountService
 */
public class L2SnipCompactor implements Compactor {

    private static final Logger log = LoggerFactory.getLogger(L2SnipCompactor.class);

    /**
     * 保留前 N 轮（初始上下文）
     */
    private static final int KEEP_HEAD_ROUNDS = 3;

    /**
     * 尾部 token 预算（~20K tokens，约 5000 个词）
     * 生产实践：laia-core、ContextCompressionEngine 都用这个方案
     */
    private static final int TAIL_TOKEN_BUDGET = 20000;

    /**
     * 至少保留的消息数量（硬最小值）
     */
    private static final int MIN_TAIL_MESSAGES = 3;

    /**
     * 触发阈值：当 token 使用率超过此值时触发 L2 Snip
     * 默认 60%，在 L4 之前触发
     */
    private static final double L2_TRIGGER_RATIO = 0.60;

    /**
     * Token 计数服务（复用已有的 JTokkitTokenCountService）
     */
    private final TokenCountService tokenCountService;

    /**
     * 模型最大上下文窗口（默认 200K tokens）
     */
    private final int maxContextTokens;

    public L2SnipCompactor(TokenCountService tokenCountService, int maxContextTokens) {
        this.tokenCountService = tokenCountService;
        this.maxContextTokens = maxContextTokens;
    }

    @Override
    public int order() {
        return 20;  // 在 L1（10）之后，L3（30）之前
    }

    @Override
    public List<Message> compact(List<Message> messages, Set<Integer> protectedIndices) {
        // 计算当前 token 使用率
        int currentTokens = tokenCountService.estimateTokens(messages);
        double usageRatio = (double) currentTokens / maxContextTokens;

        if (usageRatio < L2_TRIGGER_RATIO) {
            log.debug("L2 Snip: token 使用率 {} < {}，跳过",
                String.format("%.1f%%", usageRatio * 100),
                String.format("%.1f%%", L2_TRIGGER_RATIO * 100));
            return messages;
        }

        log.info("L2 Snip: token 使用率 {} >= {}，触发截断",
            String.format("%.1f%%", usageRatio * 100),
            String.format("%.1f%%", L2_TRIGGER_RATIO * 100));

        // 计算轮次边界
        List<int[]> roundBounds = findRoundBounds(messages);

        int headEnd = KEEP_HEAD_ROUNDS;

        // 基于 token 预算计算尾部起始位置
        int tailStart = findTailByTokenBudget(messages, headEnd, TAIL_TOKEN_BUDGET);

        // 确保至少保留 MIN_TAIL_MESSAGES 条消息
        int minTailStart = Math.max(headEnd + 1, messages.size() - MIN_TAIL_MESSAGES);
        tailStart = Math.min(tailStart, minTailStart);

        // 确保 headEnd < tailStart
        if (headEnd >= tailStart) {
            log.debug("L2 Snip: headEnd {} >= tailStart {}，跳过", headEnd, tailStart);
            return messages;
        }

        // 转换为消息索引
        int headMsgIndex = headEnd < roundBounds.size() ? roundBounds.get(headEnd)[0] : messages.size();
        int tailMsgIndex = tailStart < roundBounds.size() ? roundBounds.get(tailStart)[0] : messages.size();

        // 边界保护：确保 tool_use 和 tool_result 不分离
        headMsgIndex = adjustForToolPairs(messages, headMsgIndex, true);
        tailMsgIndex = adjustForToolPairs(messages, tailMsgIndex, false);

        // 中间全部删除
        int snipped = tailMsgIndex - headMsgIndex;
        List<Message> result = new ArrayList<>();
        result.addAll(messages.subList(0, headMsgIndex));
        result.add(Message.assistant("[snipped " + snipped + " messages from conversation middle]"));
        result.addAll(messages.subList(tailMsgIndex, messages.size()));

        log.info("L2 Snip: 删除 {} 条消息（前 {} 轮 + 后约 {} tokens）",
            snipped, headEnd, TAIL_TOKEN_BUDGET);
        return result;
    }

    /**
     * 基于 token 预算计算尾部起始位置
     *
     * 从后往前扫描，累积 token 数，直到达到预算上限。
     */
    private int findTailByTokenBudget(List<Message> messages, int headEnd, int tokenBudget) {
        int accumulated = 0;
        int cutIdx = messages.size();

        for (int i = messages.size() - 1; i >= headEnd; i--) {
            Message msg = messages.get(i);
            int msgTokens = tokenCountService.estimateTokens(msg);

            // 如果加上这条消息会超预算，且已经保留了至少 MIN_TAIL_MESSAGES 条
            if (accumulated + msgTokens > tokenBudget && (messages.size() - i) >= MIN_TAIL_MESSAGES) {
                break;
            }

            accumulated += msgTokens;
            cutIdx = i;
        }

        return cutIdx;
    }

    /**
     * 调整索引以确保 tool_use 和 tool_result 不分离
     */
    private int adjustForToolPairs(List<Message> messages, int index, boolean isHead) {
        if (isHead) {
            if (index > 0 && index <= messages.size()) {
                Message prevMsg = messages.get(index - 1);
                if (hasToolUse(prevMsg)) {
                    while (index < messages.size() && isToolResult(messages.get(index))) {
                        index++;
                    }
                }
            }
        } else {
            if (index > 0 && index < messages.size()) {
                Message currentMsg = messages.get(index);
                Message prevMsg = messages.get(index - 1);
                if (isToolResult(currentMsg) && hasToolUse(prevMsg)) {
                    index--;
                }
            }
        }
        return index;
    }

    /**
     * 计算消息列表中 ReAct 步骤的边界
     */
    static List<int[]> findRoundBounds(List<Message> messages) {
        List<int[]> bounds = new ArrayList<>();
        int i = 0;
        if (!messages.isEmpty() && messages.get(0).getRole() == Message.Role.SYSTEM) {
            i = 1;
        }
        while (i < messages.size()) {
            if (messages.get(i).getRole() != Message.Role.ASSISTANT) {
                i++;
                continue;
            }
            int stepStart = i;
            i++;
            while (i < messages.size() && messages.get(i).getRole() == Message.Role.TOOL) {
                i++;
            }
            bounds.add(new int[]{stepStart, i - 1});
        }
        return bounds;
    }

    private boolean hasToolUse(Message msg) {
        if (msg.getRole() != Message.Role.ASSISTANT) {
            return false;
        }
        return msg.hasToolCalls() && !msg.getToolCalls().isEmpty();
    }

    private boolean isToolResult(Message msg) {
        return msg.getRole() == Message.Role.TOOL && msg.getToolCallId() != null;
    }
}
```

#### 5.3.4 触发条件

```java
// L2 Snip 触发条件：基于 token 计数
// 复用已有的 JTokkitTokenCountService 和 TokenBudget

int currentTokens = tokenCountService.estimateTokens(messages);
double usageRatio = (double) currentTokens / maxContextTokens;

// 当 token 使用率超过 60% 时触发 L2 Snip
if (usageRatio >= L2_TRIGGER_RATIO) {
    result = l2SnipCompactor.compact(result, protectedIndices);
}
```

#### 5.3.5 与原 L1 的对比

| 维度 | 原 L1 (L1SnipCompactor) | 新 L2 (L2SnipCompactor) |
|------|------------------------|------------------------|
| **保留策略** | 保留最近 N 轮 | 保留前 3 轮 + 最后 ~20K tokens |
| **删除策略** | 从最旧轮次开始删除 | 中间全部删除 |
| **触发条件** | roundCount > 30 | token 使用率 > 60% |
| **边界保护** | 无 | 有（tool_use/tool_result 成对） |
| **灵活性** | 低（固定轮数） | 高（基于 token 预算） |
| **计数方式** | 轮次数 | token 数（复用已有服务） |

---

### 5.4 第 3 层：L3MicroCompactor（清理旧工具结果）

#### 5.4.1 设计目标

改造现有的 `L2MicroCompactor`，而不是新建组件：

- **原 L2**：只缩写非 Read/Grep 的工具结果（17.4%），效果有限
- **新 L3**：清理旧工具结果（不是全部，只清理旧的）
- **保留策略**：保留最近 N 个工具结果（默认5个）
- **占位符**："[Tool result deleted to save context space]"
- **利用现有缓存**：完整内容在 LRU 缓存中（15 个文件），需要时可以恢复
- **触发条件**：基于 token 计数，而不是轮次数

#### 5.4.2 改造方案

```java
package com.codemind.context;

import com.codemind.llm.Message;
import com.codemind.llm.ToolCall;
import com.codemind.session.JTokkitTokenCountService;
import com.codemind.session.TokenCountService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/**
 * L3 微压缩器：清理旧工具结果，保留最近 N 个。
 *
 * 改造自原 L2MicroCompactor：
 * - 原：只缩写非 Read/Grep 的工具结果（17.4%）
 * - 新：清理旧工具结果（不是全部，只清理旧的）
 * - 保留最近 KEEP_RECENT_TOOL_RESULTS 个
 * - 完整内容在 LRU 缓存中，需要时可以恢复
 *
 * 触发条件：
 * - 基于 token 计数，而不是轮次数
 * - 复用已有的 JTokkitTokenCountService
 */
public class L3MicroCompactor implements Compactor {

    private static final Logger log = LoggerFactory.getLogger(L3MicroCompactor.class);

    /**
     * 保留最近的工具结果数量（默认5个）
     */
    private static final int KEEP_RECENT_TOOL_RESULTS = 5;

    /**
     * 可压缩的工具列表（内容可以重新读取或执行）
     */
    private static final Set<String> COMPACTABLE_TOOLS = Set.of(
        "Read", "Bash", "Grep", "Glob", "WebFetch", "WebSearch", "Edit", "Write"
    );

    /**
     * 触发阈值：当 token 使用率超过此值时触发 L3 Micro
     * 默认 70%，在 L2（60%）之后，L4（90%）之前
     */
    private static final double L3_TRIGGER_RATIO = 0.70;

    /**
     * Token 计数服务（复用已有的 JTokkitTokenCountService）
     */
    private final TokenCountService tokenCountService;

    /**
     * 模型最大上下文窗口（默认 200K tokens）
     */
    private final int maxContextTokens;

    public L3MicroCompactor(TokenCountService tokenCountService, int maxContextTokens) {
        this.tokenCountService = tokenCountService;
        this.maxContextTokens = maxContextTokens;
    }

    @Override
    public int order() {
        return 30;  // 在 L2（20）之后，L4（40）之前
    }

    @Override
    public List<Message> compact(List<Message> messages, Set<Integer> protectedIndices) {
        // 计算当前 token 使用率
        int currentTokens = tokenCountService.estimateTokens(messages);
        double usageRatio = (double) currentTokens / maxContextTokens;

        if (usageRatio < L3_TRIGGER_RATIO) {
            log.debug("L3 Micro: token 使用率 {} < {}，跳过",
                String.format("%.1f%%", usageRatio * 100),
                String.format("%.1f%%", L3_TRIGGER_RATIO * 100));
            return messages;
        }

        log.info("L3 Micro: token 使用率 {} >= {}，触发清理工具结果",
            String.format("%.1f%%", usageRatio * 100),
            String.format("%.1f%%", L3_TRIGGER_RATIO * 100));

        // 找到所有可压缩的工具结果
        List<Integer> compactableIndices = findCompactableToolResults(messages);

        // 如果可压缩的工具结果数量 <= KEEP_RECENT_TOOL_RESULTS，则跳过
        if (compactableIndices.size() <= KEEP_RECENT_TOOL_RESULTS) {
            log.debug("L3 Micro: 可压缩工具结果数量 {} ≤ {}，跳过",
                compactableIndices.size(), KEEP_RECENT_TOOL_RESULTS);
            return messages;
        }

        List<Message> result = new ArrayList<>(messages);
        int toClear = compactableIndices.size() - KEEP_RECENT_TOOL_RESULTS;
        int cleared = 0;

        // 清理旧的工具结果（保留最近N个）
        for (int i = 0; i < toClear; i++) {
            int idx = compactableIndices.get(i);
            Message msg = result.get(idx);

            // 保留 tool_use（用户侧），删除 tool_result 内容
            // 替换为占位符
            result.set(idx, Message.tool(
                "[Tool result deleted to save context space]\n" +
                "Tool: " + findToolName(messages, idx) + "\n" +
                "Re-run to get fresh results.",
                msg.getToolCallId()
            ));
            cleared++;
        }

        if (cleared > 0) {
            log.info("L3 Micro: 清理 {} 个工具结果，保留最近 {} 个",
                cleared, KEEP_RECENT_TOOL_RESULTS);
        }

        return result;
    }

    /**
     * 找到所有可压缩的工具结果
     * 只压缩 COMPACTABLE_TOOLS 中的工具
     * 
     * 阈值说明：只清理内容长度 > 120 字符的工具结果
     * 原因：
     * 1. 小结果（<120字符）通常是错误信息或简短确认，保留它们有助于上下文理解
     * 2. 大结果（>120字符）通常是文件内容或命令输出，清理它们可以节省大量token
     * 3. 120字符约等于1-2行文本，是"有意义内容"的最小阈值
     */
    private List<Integer> findCompactableToolResults(List<Message> messages) {
        List<Integer> indices = new ArrayList<>();

        for (int i = 0; i < messages.size(); i++) {
            Message msg = messages.get(i);

            if (msg.getRole() != Message.Role.TOOL || msg.getToolCallId() == null) {
                continue;
            }

            String toolName = findToolName(messages, i);
            if (toolName != null && COMPACTABLE_TOOLS.contains(toolName)) {
                if (msg.getContent() != null && msg.getContent().length() > 120) {
                    indices.add(i);
                }
            }
        }

        return indices;
    }

    private String findToolName(List<Message> messages, int toolIndex) {
        if (toolIndex < 0 || toolIndex >= messages.size()) return null;

        Message toolMsg = messages.get(toolIndex);
        String toolCallId = toolMsg.getToolCallId();
        if (toolCallId == null) return null;

        for (int i = toolIndex - 1; i >= 0; i--) {
            Message msg = messages.get(i);
            if (msg.getRole() == Message.Role.ASSISTANT && msg.hasToolCalls()) {
                for (ToolCall tc : msg.getToolCalls()) {
                    if (toolCallId.equals(tc.getId())) {
                        return tc.getName();
                    }
                }
            }
        }
        return null;
    }
}
```

#### 5.4.3 触发条件

```java
// L3 Micro 触发条件：基于 token 计数
// 复用已有的 JTokkitTokenCountService 和 TokenBudget

int currentTokens = tokenCountService.estimateTokens(messages);
double usageRatio = (double) currentTokens / maxContextTokens;

// 当 token 使用率超过 70% 时触发 L3 Micro
if (usageRatio >= L3_TRIGGER_RATIO) {
    result = l3MicroCompactor.compact(result, protectedIndices);
}
```

#### 5.4.4 与现有缓存的协作

```
L3 清空工具结果后：
1. 完整内容仍在 LRU 缓存中（15 个文件）
2. L4 摘要时，缓存内容会注入到摘要 prompt 中
3. 模型需要时，可以重新 Read 获取完整内容
```

#### 5.4.5 与原 L2 的对比

| 维度 | 原 L2 (L2MicroCompactor) | 新 L3 (L3MicroCompactor) |
|------|------------------------|------------------------|
| **处理范围** | 只缩写非 Read/Grep（17.4%） | 清理可压缩工具结果（100%） |
| **保留策略** | 保留最近 N 轮完整 | 保留最近 5 个工具结果 |
| **占位符** | "[Earlier tool result compacted.]" | "[Tool result deleted to save context space]" |
| **缓存协作** | 无 | 利用 LRU 缓存恢复 |
| **触发条件** | roundCount > 25 | token 使用率 > 70% |
| **消息结构** | 保留 tool_use + tool_result | 保留 tool_use，删除 tool_result 内容 |

**说明**：L3 清理可压缩工具结果（100%指所有可压缩工具，但只清理旧的，保留最近5个）。

---

### 5.5 第 4 层：L4SummaryCompactor（LLM 摘要保底）

#### 5.5.1 设计目标

- 全量重写对话（9 段式结构化摘要）
- 注入缓存的文件内容（最近 5 个文件）
- 触发：token > 90%（紧急逃生阀，留出 10% 缓冲）
- 基于 token 计数，而不是轮次数
- **保留最近 3 轮完整对话**（与 L2 的"保留前 3 轮"不同）

#### 5.5.2 当前实现（保持不变）

当前 L4 实现已经很好：
- 使用 LLM 做全量摘要
- 注入缓存的文件内容
- 保留最近 3 轮完整对话

#### 5.5.3 改进点

```java
// 改进：在 L4 摘要时，注入缓存的最近 5 个文件（借鉴 Claude Code）

private String getCachedFileContentForPrompt(Map<String, String> fileContentCache) {
    if (fileContentCache == null || fileContentCache.isEmpty()) return "";
    
    // 只注入最近 5 个文件（借鉴 Claude Code 的设计）
    List<Map.Entry<String, String>> recentEntries = getRecentEntries(fileContentCache, 5);
    
    StringBuilder sb = new StringBuilder();
    sb.append("\n\n=== Recently Read Files (guaranteed full content, do not omit) ===\n");
    for (Map.Entry<String, String> entry : recentEntries) {
        sb.append("\n--- ").append(entry.getKey()).append(" ---\n");
        sb.append(entry.getValue()).append("\n");
    }
    return sb.toString();
}

private List<Map.Entry<String, String>> getRecentEntries(Map<String, String> cache, int limit) {
    // LinkedHashMap 按访问顺序排列，最后访问的在最后
    List<Map.Entry<String, String>> entries = new ArrayList<>(cache.entrySet());
    int start = Math.max(0, entries.size() - limit);
    return entries.subList(start, entries.size());
}
```

#### 5.5.4 触发条件

```java
// L4 Summary 触发条件：基于 token 计数
// 复用已有的 JTokkitTokenCountService 和 TokenBudget

int currentTokens = tokenCountService.estimateTokens(messages);
double usageRatio = (double) currentTokens / maxContextTokens;

// 当 token 使用率超过 90% 时触发 L4 Summary（紧急逃生阀）
if (usageRatio >= L4_TRIGGER_RATIO) {
    result = l4SummaryCompactor.compact(result, protectedIndices);
}
```

---

## 六、实施计划

### 6.1 Phase 1：L3 改造（2-3 天）

#### 6.1.1 任务清单

- [ ] 修改 `L2MicroCompactor.java` → 重命名为 `L3MicroCompactor.java`
- [ ] 实现清理旧工具结果（保留最近5个）
- [ ] 实现只保留最近 5 个工具结果
- [ ] 添加元数据占位符
- [ ] 实现基于 token 计数的触发条件（复用 `JTokkitTokenCountService`）
- [ ] 编写单元测试
- [ ] 集成测试

#### 6.1.2 验证标准

- [ ] L3 能正确清理旧工具结果
- [ ] 只保留最近 5 个工具结果
- [ ] LRU 缓存中的文件内容仍然可用
- [ ] L4 摘要时能正确注入缓存内容
- [ ] 触发条件基于 token 计数（70% 阈值）

### 6.2 Phase 2：L2 Snip 改造（2-3 天）

#### 6.2.1 任务清单

- [ ] 修改 `L1SnipCompactor.java` → 重命名为 `L2SnipCompactor.java`
- [ ] 实现基于 token 预算的保留策略（前 3 轮 + 最后 ~20K tokens）
- [ ] 实现边界保护（tool_use/tool_result 不分离）
- [ ] 实现基于 token 计数的触发条件（复用 `JTokkitTokenCountService`）
- [ ] 编写单元测试
- [ ] 集成测试

#### 6.2.2 验证标准

- [ ] L2 Snip 能正确计算 token 预算
- [ ] 保留前 3 轮 + 最后 ~20K tokens
- [ ] 中间全部删除
- [ ] 边界保护：tool_use 和 tool_result 不分离
- [ ] 触发条件基于 token 计数（60% 阈值）

### 6.3 Phase 3：L1 Spill 保持不变（0.5 天）

#### 6.3.1 任务清单

- [ ] 重命名 `L3SpillCompactor.java` → `L1SpillCompactor.java`
- [ ] 验证现有实现无需修改

#### 6.3.2 验证标准

- [ ] L1 Spill 正常工作
- [ ] 大结果（>50K）正确落盘

### 6.4 Phase 4：L4 优化（1 天）

#### 6.4.1 任务清单

- [ ] 优化 `getCachedFileContentForPrompt()` 方法
- [ ] 只注入最近 5 个文件
- [ ] 验证 L4 摘要质量
- [ ] 实现基于 token 计数的触发条件（复用 `JTokkitTokenCountService`）

#### 6.4.2 验证标准

- [ ] L4 摘要时正确注入最近 5 个文件
- [ ] 摘要质量符合预期
- [ ] 触发条件基于 token 计数（90% 阈值）

### 6.5 Phase 5：集成测试（1-2 天）

#### 6.5.1 任务清单

- [ ] 端到端测试
- [ ] 性能测试
- [ ] 收集真实会话数据
- [ ] 调优参数

#### 6.5.2 验证标准

- [ ] 压缩管线正常工作
- [ ] 上下文连续性良好
- [ ] 性能开销可接受

---

## 七、参数配置

### 7.1 L2 Snip 参数

| 参数 | 默认值 | 说明 |
|------|--------|------|
| `KEEP_HEAD_ROUNDS` | 3 | 保留前 N 轮（初始上下文） |
| `TAIL_TOKEN_BUDGET` | 20000 | 尾部 token 预算（~20K tokens） |
| `MIN_TAIL_MESSAGES` | 3 | 至少保留的消息数量 |
| `L2_TRIGGER_RATIO` | 0.60 | token 使用率触发阈值（60%） |
| `MAX_CONTEXT_TOKENS` | 200000 | 模型最大上下文窗口（200K tokens） |

### 7.2 L3 Micro 参数

| 参数 | 默认值 | 说明 |
|------|--------|------|
| `KEEP_RECENT_TOOL_RESULTS` | 5 | 保留最近的工具结果数量 |
| `L3_TRIGGER_RATIO` | 0.70 | token 使用率触发阈值（70%） |
| `MAX_CONTEXT_TOKENS` | 200000 | 模型最大上下文窗口（200K tokens） |

### 7.3 L4 Summary 参数

| 参数 | 默认值 | 说明 |
|------|--------|------|
| `L4_TRIGGER_RATIO` | 0.90 | token 使用率触发阈值（90%） |
| `KEEP_RECENT_ROUNDS_L4` | 3 | 保留最近的轮次数 |
| `MAX_CACHED_FILES_L4` | 5 | 注入最近的文件数量 |
| `MAX_CONTEXT_TOKENS` | 200000 | 模型最大上下文窗口（200K tokens） |

---

## 八、验证方案

### 8.1 单元测试

#### 8.1.1 L3 Micro 测试

```java
@Test
public void testL3ClearsOldToolResults() {
    // 准备：10 个工具结果
    List<Message> messages = createMessagesWithToolResults(10);
    
    // 模拟 token 使用率 > 70%
    when(tokenCountService.estimateTokens(messages)).thenReturn(140000);
    
    // 执行 L3 Micro
    List<Message> result = l3MicroCompactor.compact(messages, Set.of());
    
    // 验证：只保留最近 5 个，清理 5 个旧的
    assertEquals(5, countUnclearedToolResults(result));
    assertEquals(5, countClearedToolResults(result));
}

@Test
public void testL3PreservesRecentToolResults() {
    // 准备：3 个工具结果
    List<Message> messages = createMessagesWithToolResults(3);
    
    // 模拟 token 使用率 > 70%
    when(tokenCountService.estimateTokens(messages)).thenReturn(140000);
    
    // 执行 L3 Micro
    List<Message> result = l3MicroCompactor.compact(messages, Set.of());
    
    // 验证：全部保留（3 <= KEEP_RECENT_TOOL_RESULTS）
    assertEquals(3, countUnclearedToolResults(result));
    assertEquals(0, countClearedToolResults(result));
}

@Test
public void testL3SkipsWhenUsageBelowThreshold() {
    // 准备：10 个工具结果
    List<Message> messages = createMessagesWithToolResults(10);
    
    // 模拟 token 使用率 < 70%
    when(tokenCountService.estimateTokens(messages)).thenReturn(100000);
    
    // 执行 L3 Micro
    List<Message> result = l3MicroCompactor.compact(messages, Set.of());
    
    // 验证：全部保留（未触发）
    assertEquals(10, countUnclearedToolResults(result));
    assertEquals(0, countClearedToolResults(result));
}
```

#### 8.1.2 L2 Snip 测试

```java
@Test
public void testL2SnipKeepsHeadAndTail() {
    // 准备：大对话（token 使用率 > 60%）
    List<Message> messages = createLargeConversation(100);
    
    // 模拟 token 使用率 > 60%
    when(tokenCountService.estimateTokens(messages)).thenReturn(120000);
    
    // 执行 L2 Snip
    List<Message> result = l2SnipCompactor.compact(messages, Set.of());
    
    // 验证：保留前 3 轮 + 最后 ~20K tokens
    assertTrue(containsHeadRounds(result, 3));
    assertTrue(containsTailTokens(result, 20000));
}

@Test
public void testL2SnipProtectsToolPairs() {
    // 准备：包含 tool_use 和 tool_result 的消息
    List<Message> messages = createMessagesWithToolPairs();
    
    // 模拟 token 使用率 > 60%
    when(tokenCountService.estimateTokens(messages)).thenReturn(120000);
    
    // 执行 L2 Snip
    List<Message> result = l2SnipCompactor.compact(messages, Set.of());
    
    // 验证：tool_use 和 tool_result 没有被分离
    assertTrue(allToolPairsIntact(result));
}

@Test
public void testL2SnipSkipsWhenUsageBelowThreshold() {
    // 准备：小对话
    List<Message> messages = createSmallConversation(10);
    
    // 模拟 token 使用率 < 60%
    when(tokenCountService.estimateTokens(messages)).thenReturn(60000);
    
    // 执行 L2 Snip
    List<Message> result = l2SnipCompactor.compact(messages, Set.of());
    
    // 验证：消息未被修改
    assertEquals(messages.size(), result.size());
}
```

### 8.2 集成测试

#### 8.2.1 端到端测试

```java
@Test
public void testFullCompressionPipeline() {
    // 准备：模拟大对话（token 使用率 > 90%）
    List<Message> messages = createLargeConversation(100);
    
    // 模拟 token 使用率 > 90%
    when(tokenCountService.estimateTokens(messages)).thenReturn(180000);
    
    // 执行压缩管线
    CompressionResult result = orchestrator.run(messages, null);
    
    // 验证：压缩成功
    assertTrue(result.didCompact());
    assertTrue(result.compressedMessages().size() < messages.size());
    
    // 验证：关键信息保留
    assertTrue(containsUserMessages(result.compressedMessages()));
    assertTrue(containsFileModifications(result.compressedMessages()));
}
```

#### 8.2.2 性能测试

```java
@Test
public void testCompressionPerformance() {
    // 准备：大对话（100 轮）
    List<Message> messages = createLargeConversation(100);
    
    // 模拟 token 使用率 > 90%
    when(tokenCountService.estimateTokens(messages)).thenReturn(180000);
    
    // 测量压缩时间
    long startTime = System.currentTimeMillis();
    orchestrator.run(messages, null);
    long duration = System.currentTimeMillis() - startTime;
    
    // 验证：压缩时间在可接受范围内（< 100ms）
    assertTrue(duration < 100);
}
```

### 8.3 真实会话测试

#### 8.3.1 测试流程

1. 收集 10 个真实会话（已有数据）
2. 对每个会话执行压缩管线
3. 分析压缩效果：
   - 压缩率（压缩后 token / 压缩前 token）
   - 关键信息保留率
   - 上下文连续性
4. 调优参数

#### 8.3.2 评估指标

| 指标 | 目标 | 说明 |
|------|------|------|
| **压缩率** | < 50% | 压缩后 token 应小于 50% |
| **关键信息保留率** | > 90% | 用户消息、文件修改、错误修复应保留 |
| **上下文连续性** | 良好 | 压缩后对话应能无缝接续 |

---

## 九、风险评估

### 9.1 技术风险

| 风险 | 影响 | 缓解措施 |
|------|------|---------|
| **L3 过度清理** | 丢失重要工具结果 | 只清理 COMPACTABLE_TOOLS 中的工具，保留最近 N 个结果 |
| **L2 Snip 误删重要轮次** | 丢失关键信息 | 保留前 3 轮 + 最后 ~20K tokens，中间全部删除（确定性行为） |
| **缓存失效** | 无法恢复文件内容 | LRU 缓存持续更新，L4 摘要时注入缓存内容 |
| **Token 计数不准确** | 触发条件误判 | 复用已有的 `JTokkitTokenCountService`（tiktoken Java 端口） |
| **消息结构破坏** | 模型无法理解上下文 | 保留 tool_use，删除 tool_result 内容，保持消息配对完整 |

### 9.2 性能风险

| 风险 | 影响 | 缓解措施 |
|------|------|---------|
| **Token 计算开销** | 每次压缩增加计算时间 | 复用已有服务，O(n) 复杂度 |
| **缓存内存占用** | 内存消耗增加 | LRU 缓存限制 15 个文件，每个文件最多 200K 字符 |

---

## 十、Oracle 专业建议

### 10.1 Oracle 的关键发现

基于 Oracle 的深度分析，当前设计的核心问题：

| 问题 | Oracle 分析 | 影响 |
|------|------------|------|
| **L3 几乎无效** | Read 占 82.6%，L3 只缩写非 Read/Grep | L3 很少有东西可缩写 |
| **L2 过于激进** | 一次删除 15 轮，没有考虑内容重要性 | 可能丢失关键信息 |
| **触发频率** | 每次 THINK 都检查 | 可能有性能开销 |
| **触发条件不精确** | 基于轮次数，而不是 token 计数 | 无法精确控制压缩时机 |

**注意**：Oracle 的分析是基于旧设计的，新设计已经解决了这些问题。

### 10.2 Oracle 的修复方向

Oracle 提出了三种可能的修复方向：

```
A) 让 L2 更温和但更频繁
   - 每次删 3-5 轮而不是 15 轮
   - 更频繁地触发，但每次删除更少

B) 让 L2 更智能
   - 保护有关键决策/发现的轮次
   - 基于内容重要性决定删除哪些

C) 借鉴 Claude Code 的简单规则（最终选择）
   - 保留前 3 轮 + 最后 ~20K tokens
   - 中间全部删除
   - 边界保护：tool_use/tool_result 不分离
   - 简单可靠，可预测
   - 基于 token 计数触发，更精确
```

### 10.3 Oracle 建议的整合

基于 Oracle 的建议和 Claude Code 的实践，改造方案调整为：

| 改进项 | Oracle 建议 | 整合后的方案 |
|--------|------------|-------------|
| **L2 Snip** | 简单规则 + 边界保护 | L2 Snip：保留前 3 轮 + 最后 ~20K tokens |
| **L3 Micro** | 无效（Read 占 82.6%） | L3 改造：清理旧工具结果，保留最近5个 |
| **触发条件** | 基于轮次数 | 基于 token 计数（复用已有 `JTokkitTokenCountService`） |
| **触发频率** | 每次 THINK 都检查 | 保持（有早期退出，开销可接受） |

---

## 十一、总结

### 11.1 改进要点

1. **改造 L3**：清理旧工具结果，只保留最近 5 个，保留 tool_use，删除 tool_result 内容
2. **改进 L2**：L2 Snip：保留前 3 轮 + 最后 ~20K tokens，中间全部删除
3. **优化 L4 摘要**：注入最近 5 个文件的内容
4. **统一触发条件**：所有压缩层都基于 token 计数，复用已有的 `JTokkitTokenCountService`

### 11.2 预期效果

| 指标 | 当前 | 改进后 |
|------|------|--------|
| **L1 Spill** | 保持不变 | 保持不变（大结果落盘） |
| **L2 Snip** | 一刀切 15 轮 | L2 Snip：保留前 3 轮 + 最后 ~20K tokens（token 使用率 > 60%） |
| **L3 Micro** | 只处理 17.4%（几乎无效） | L3 Micro：清理旧工具结果，保留最近5个（token 使用率 > 70%） |
| **L4 Summary** | 95%（几乎不用） | L4 Summary：token 使用率 > 90%（紧急逃生阀） |
| **触发方式** | 轮次数 | token 计数（复用已有服务） |
| **信息保留** | 占位符替换 | 保留 tool_use，删除 tool_result 内容 |
| **压缩率** | ~50% | < 30% |
| **上下文连续性** | 差 | 良好 |

### 11.3 参考资料

- Claude Code 源码分析：https://xiaolinnote.com/claudecode/source/cc_compact.html
- CodeMind 压缩设计文档：COMPRESSION_DESIGN.md
- CodeMind 压缩图表：COMPRESSION_DIAGRAMS.md
- Context Compaction Study：https://wasnotwas.com/writing/context-compaction/
- Claude Code 四层架构：https://blog.4sapi.com/blog/claude-code-four-tier-context-compression
- SWE-Pruner 论文：https://arxiv.org/abs/2601.16746
- LaMR 论文：https://arxiv.org/abs/2605.15315
- Pi Context Prune：https://github.com/championswimmer/pi-context-prune
- Context-Chef Middleware：https://github.com/MyPrototypeWhat/context-chef
- OpenCode filterCompacted：https://github.com/anomalyco/opencode/blob/dev/packages/opencode/src/session/message-v2.ts

---

*文档版本：v2.0*
*创建日期：2026-07-14*
*更新日期：2026-07-14*
*作者：Sisyphus*
