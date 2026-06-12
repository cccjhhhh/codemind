---
name: analyze_logs
description: 对日志文件进行深度分析，识别异常模式、统计错误分布、定位根因并提供可操作改进建议。适用于排查线上问题、分析错误堆栈、诊断系统故障等场景。
triggerKeywords:
  - 分析日志
  - analyze logs
  - 查看日志
  - 日志分析
  - parse logs
  - 检查日志
  - 排查问题
  - 日志排查
  - 日志检查
  - 错误日志
  - error log
  - 看日志
  - 日志诊断
  - 异常分析
  - troubleshoot logs
  - check logs
  - view logs
  - 日志聚合
  - 日志统计
  - 日志报告
  - tail log
  - 日志追踪
  - 故障分析
disabledKeywords:
  - 跳过日志
  - skip logs
  - 不要分析日志
---

# Analyze Logs Skill

IRON LAW: 这是 analysis-first workflow。除非用户明确要求，否则只输出分析报告，不修改任何日志文件或系统配置。

## 什么时候启用

当用户要求分析日志、排查线上问题、查看错误堆栈、或诊断系统故障时。典型场景：

- 用户说了"分析一下这个日志"、"看看报错"、"排查一下问题"
- 用户贴了日志片段或堆栈信息
- 用户反馈系统异常、服务不可用、性能下降
- CI/CD 构建日志分析

## 日志格式识别

首先确定日志格式，不同格式采用不同的解析策略：

| 格式 | 特征 | 解析要点 |
|------|------|----------|
| **Java/Spring Boot** | `2024-01-01 12:00:00.123 [thread] LEVEL com.example.Class - message` | 时间戳+级别+线程+类名 |
| **Java 堆栈** | 以 `Exception`/`Error` 开头，包含 `at ...(...)` 调用链 | 提取根因异常和关键调用栈 |
| **系统日志 (Linux)** | `Jan 1 12:00:00 hostname service[pid]: message` | syslog 格式 |
| **系统日志 (Windows)** | `日期, 时间, 类别, 类型, 事件ID, 来源, 消息` | EventLog 格式 |
| **Nginx/Access Log** | `IP - - [01/Jan/2024:12:00:00 +0000] "GET /path HTTP/1.1" 200 1234` | 状态码+请求路径+响应时间 |
| **JSON 日志** | 每行一个 JSON 对象，包含 `timestamp`, `level`, `message` 等字段 | 结构化解析 |
| **自定义格式** | 无标准格式的文本日志 | 先采样识别分隔符和字段位置 |

## 分析工作流

```
Log Analysis Progress:

- [ ] Step 1: 识别日志来源和格式
  - [ ] 确认日志文件路径和大小（大文件只读取关键部分）
  - [ ] 采样前 20 行或尾部 100 行判断日志格式
  - [ ] 确定时间范围和分析窗口

- [ ] Step 2: 加载和解析
  - [ ] 使用 Read 工具读取日志（大文件用 tail 或按时间范围截取）
  - [ ] 解析每条日志的时间戳、级别、消息三要素
  - [ ] 对多行日志（如堆栈轨迹）进行聚合

- [ ] Step 3: 异常检测
  - [ ] 按日志级别分组统计（ERROR / WARN / INFO / DEBUG）
  - [ ] 提取所有异常和错误消息，去重归类
  - [ ] 对每个异常提取根因（查看完整堆栈，找到 Caused by）
  - [ ] 匹配通用异常模式库（见下方）

- [ ] Step 4: 频次与趋势分析
  - [ ] 统计每个错误模式的出现频次
  - [ ] 识别时间维度上的突发模式（spike / 持续增长 / 周期性）
  - [ ] 检查错误之间的时序关联（A 错误是否先于 B 错误）

- [ ] Step 5: 关联分析
  - [ ] 检查同一时间窗口内的其他相关日志
  - [ ] 识别跨服务的错误传播链
  - [ ] 检查系统资源相关日志（OOM、CPU、GC）

- [ ] Step 6: 生成分析报告
  - [ ] 按 P0-P3 严重性分级输出
  - [ ] 给出可操作的建议
  - [ ] 标注关键日志行号和时间戳
```

## 通用异常模式库

分析时自动匹配以下常见模式。这不是穷举列表，而是需要重点关注的模式：

### P0 - Critical

| 模式 | 典型日志特征 | 可能原因 | 建议 |
|------|-------------|----------|------|
| **OOM / 内存溢出** | `OutOfMemoryError`, `Java heap space`, `GC overhead limit exceeded`, `Metaspace` | 堆配置不足、内存泄漏 | 检查 JVM 堆配置、heap dump 分析、代码中集合类使用 |
| **死锁** | `Deadlock`, `deadlock`, `WAITING` 线程链 | 锁竞争、资源有序性问题 | 获取 thread dump 分析锁顺序、review 同步块 |
| **致命错误** | `JVMCI Error`, `SIGSEGV`, `SIGABRT`, `Fatal error` | JVM 崩溃、本地内存损坏 | 检查 hs_err 日志、升级 JDK 版本 |
| **数据丢失** | `WAL flush failed`, `replica lag`, `data loss detected` | 存储引擎或复制故障 | 检查集群健康、备份恢复 |

### P1 - High

| 模式 | 典型日志特征 | 可能原因 | 建议 |
|------|-------------|----------|------|
| **连接超时** | `Connection timed out`, `connect timed out`, `ConnectException`, `NoRouteToHost` | 网络故障、目标服务不可用、防火墙 | 检查网络连通性、服务健康检查、超时配置 |
| **连接拒绝** | `Connection refused`, `Connection reset`, `Broken pipe` | 目标服务未启动、端口未监听、连接池耗尽 | 检查服务状态、端口监听、连接池配置 |
| **空指针** | `NullPointerException` | 未空值检查、上游返回 null | 定位空值来源行、检查上游返回 |
| **线程池耗尽** | `Thread pool exhausted`, `RejectedExecutionException`, `Queue full` | 请求突发、线程池配置过小、任务阻塞 | 检查线程池配置、请求量、阻塞任务 |
| **OOM 被吞** | `java.lang.OutOfMemoryError: unable to create new native thread` | 线程数超过系统限制 | 检查线程数 ulimit、线程泄漏 |
| **断路器打开** | `CircuitBreaker`, `Hystrix`, `fallback`, `open circuit` | 下游服务故障 | 检查断路器配置、下游服务健康 |

### P2 - Medium

| 模式 | 典型日志特征 | 可能原因 | 建议 |
|------|-------------|----------|------|
| **GC 频繁** | `Full GC`, `GC pause`, `allocation failure` | 堆压力大、GC 参数不合理 | 检查 GC 日志、调整 GC 策略和堆大小 |
| **Socket 挂断** | `SocketException: Socket hang up`, `Connection reset by peer` | 请求端超时断开、负载均衡空闲超时 | 检查超时配置、客户端重试机制 |
| **SQL 异常** | `SQLException`, `DataIntegrityViolation`, `ConstraintViolation` | SQL 错误、数据完整性问题 | 检查 SQL 语句、数据约束 |
| **资源未关闭** | `Too many open files`, `IOException` | 文件句柄/连接泄漏 | 检查 try-with-resource、连接池配置 |

### P3 - Low

| 模式 | 典型日志特征 | 可能原因 | 建议 |
|------|-------------|----------|------|
| **配置警告** | `Configuration property is deprecated`, `Config 'x' is ignored` | 配置项废弃或无效 | 更新配置文件 |
| **重试日志** | `Retrying`, `retry attempt`, `attempt N failed` | 临时故障、健康检查重试 | 确认重试是否成功，持续重试需关注 |
| **慢请求** | `slow request`, `processing time`, `latency > threshold` | 性能瓶颈 | 检查慢请求的时间和频率 |
| **启动警告** | `WARNING`, `startup issue`, `initialization failed but continuing` | 启动阶段非致命问题 | review 启动配置和依赖服务 |

## 严重性分级

| 级别 | 名称 | 描述 | 行动 |
|------|------|------|------|
| **P0** | Critical | 系统级故障、数据丢失、服务宕机 | 必须立即修复 |
| **P1** | High | 功能受损、性能严重下降、关键错误持续出现 | 应在维护窗口修复 |
| **P2** | Medium | 非关键错误、异常模式、配置问题 | 按计划修复或创建 follow-up |
| **P3** | Low | 警告、建议、轻微异常 | 可选改进 |

## 输出格式

```markdown
## 日志分析报告

**文件**: xxx.log
**时间范围**: 2024-01-01 12:00:00 ~ 2024-01-01 14:00:00
**日志级别分布**: ERROR=12, WARN=34, INFO=256, DEBUG=0
**总体评估**: ❌ 需要关注 (P0: 1, P1: 3, P2: 5, P3: 2)

---

### P0 - Critical

1. **[行 1234] OutOfMemoryError: Java heap space**
   - **时间**: 2024-01-01 13:15:22.456
   - **堆栈摘要**: com.example.service.DataProcessor.process(DataProcessor.java:88)
   - **频次**: 在 2 分钟内出现 3 次
   - **建议**: 检查 JVM -Xmx 配置（当前 256MB），考虑增大堆内存或排查内存泄漏

### P1 - High

2. **[行 567] Connection refused: db.example.com:3306**
   - **时间**: 2024-01-01 12:30:15.123
   - **频次**: 间隔 5s 持续出现，共计 8 次
   - **建议**: 检查数据库服务状态和网络连通性
   ...

### P2 - Medium

3. **[行 890] Full GC 引起的暂停 2.3s**
   - **建议**: 检查 GC 参数，考虑使用 G1GC 并调整 -XX:MaxGCPauseMillis

### P3 - Low

4. **[行 45] 配置项 'server.xxx' 已废弃**
   - **建议**: 参考新版本迁移指南更新配置文件

---

### 时间线 (关键事件)

| 时间 | 事件 | 影响 |
|------|------|------|
| 13:15:22 | OOM 首次出现 | DataProcessor 线程终止 |
| 13:15:27 | OOM 第二次出现 | 服务进入降级模式 |
| 13:15:32 | OOM 第三次出现 | 服务拒绝请求 |
| 13:16:00 | 健康检查失败 | 注册中心摘除节点 |
```

## 确认门控

分析完成后必须询问用户下一步操作：

```markdown
## 下一步操作

分析发现 X 个问题（P0: _, P1: _, P2: _, P3: _）。

**请选择：**

1. **提供修复建议** — 对 P0/P1 问题给出详细修复方案
2. **深入分析** — 对特定问题做更深层分析（如 thread dump、heap dump 分析）
3. **关联排查** — 检查关联服务的日志
4. **导出报告** — 将分析结果保存到文件
5. **无需操作** — 分析完成

请选择或提供具体指示。
```

## 分析技巧

- **大文件处理**: 对于超过 100MB 的日志文件，使用 `tail -n 1000` / `grep ERROR` 等方式缩小范围，避免一次性读取整个文件
- **时间窗口定位**: 如果用户知道故障时间点，用 `grep` 定位该时间点前后各 5 分钟的日志
- **堆栈聚合**: 同一个异常可能出现在多个线程中，按异常类型和消息聚合，找出根因模式
- **关联上下文**: 异常堆栈后的 5-10 行日志通常包含关键上下文信息

## 依赖的 Tools

- **Read** — 读取日志文件
- **Bash** — 执行 tail、grep、head、wc 等命令行工具
- **Grep** — 在日志中搜索特定模式
- **Glob** — 查找日志文件（如 `*.log`, `*.out`, `*.gz`）
