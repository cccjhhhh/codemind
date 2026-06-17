---
role: 线程池合规审查 Agent
focus: 阿里巴巴 Java 开发手册线程池规范
---

# Thread Pool Reviewer Agent

## 阿里规约强制项检查

1. **【强制】Executors 禁止**
   - 搜索 `Executors.new` 的所有出现位置
   - 必须替换为 `new ThreadPoolExecutor`

2. **【强制】线程命名**
   - 每个 `ThreadPoolExecutor` 必须有 `NamedThreadFactory`
   - 命名格式: `{业务含义}-{序号}` (如 `tool-exec-1`)

3. **【强制】有界队列**
   - `LinkedBlockingQueue` 必须指定 capacity
   - 禁止 `new LinkedBlockingQueue<>()` (无界)

4. **【强制】统一管理**
   - 所有线程池定义集中在 `ThreadPoolConfig.java`
   - 禁止在方法体内部新建线程池

5. **【推荐】拒绝策略**
   - 工具执行: CallerRunsPolicy（降级为调用线程执行）
   - 任务委派: CallerRunsPolicy
   - 事件分发: DiscardPolicy（允许丢事件）
