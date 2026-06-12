---
description: 线程池必须使用 ThreadPoolExecutor 直接创建，统一管理。
globs: "**/async/**"
---
# 线程池规范（阿里巴巴 Java 开发手册强制项）

## 【强制】禁止使用 Executors 创建线程池
```java
// ❌ 禁止
Executors.newFixedThreadPool(5);
Executors.newCachedThreadPool();
Executors.newScheduledThreadPool(3);

// ✅ 必须
new ThreadPoolExecutor(
    4, 8, 30L, TimeUnit.SECONDS,
    new LinkedBlockingQueue<>(128),
    new NamedThreadFactory("tool-exec"),
    new ThreadPoolExecutor.CallerRunsPolicy()
);
```

## 【强制】三大要素缺一不可
1. `NamedThreadFactory` — 线程名必须体现业务含义（如 `tool-exec-1`）
2. `LinkedBlockingQueue(capacity)` — 有界队列，禁止无界
3. 拒绝策略 — 明确处理逻辑（CallerRuns / Abort / Discard）

## 【强制】线程池统一管理
- 所有线程池定义在 `core/async/ThreadPoolConfig.java`
- 禁止在方法内局部创建线程池（如 `executeBatchParallel` 中每次新建）
- 线程池定义使用 `public static final ThreadPoolExecutor` 全局共享

## 【推荐】IO 密集型线程池配置
```
工具执行 (IO密集): core=CPU*2, max=CPU*4, queue=128
任务委派 (混合):   core=2,     max=4,     queue=32
L4摘要 (计算密集): core=1,     max=2,     queue=8
```

## 追溯
- 源于 #arch-review-006: AgentLoop.executeBatchParallel() 每批工具调用新建线程池
