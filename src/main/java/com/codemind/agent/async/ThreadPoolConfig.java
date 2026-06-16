package com.codemind.agent.async;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

/**
 * 全局线程池配置（阿里巴巴 Java 开发手册强制项）。
 *
 * 【强制】ThreadPoolExecutor 不允许使用 Executors 创建，必须直接使用 ThreadPoolExecutor。
 * 【强制】必须使用有界队列 LinkedBlockingQueue(capacity)，禁止无界队列。
 * 【强制】必须明确线程名称（通过 NamedThreadFactory），便于问题排查。
 * 【强制】线程池统一管理，禁止在方法内局部创建。
 *
 * 线程池类型与规格：
 * <pre>
 * 名称             core  max  queue  用途             类型
 * tool-exec         4     8    128   Tool 并行执行    IO 密集型
 * task-delegate     2     4     32   Task 子任务委派  混合型
 * l4-summary        1     2      8   L4 摘要         计算密集型
 * </pre>
 */
public final class ThreadPoolConfig {

    private static final Logger log = LoggerFactory.getLogger(ThreadPoolConfig.class);

    /** Tool 并行执行线程池：IO 密集型，core=CPU*2 */
    public static final ThreadPoolExecutor TOOL_EXEC = new ThreadPoolExecutor(
            4, 8,
            30L, TimeUnit.SECONDS,
            new LinkedBlockingQueue<>(128),
            new NamedThreadFactory("tool-exec"),
            (r, e) -> {
                log.warn("tool-exec 队列已满(128)，任务拒绝后由调用线程执行: {}", r);
                if (!e.isShutdown()) {
                    r.run();
                }
            }
    );

    /** Task 子任务委派线程池：混合型 */
    public static final ThreadPoolExecutor TASK_DELEGATE = new ThreadPoolExecutor(
            2, 4,
            60L, TimeUnit.SECONDS,
            new LinkedBlockingQueue<>(32),
            new NamedThreadFactory("task-delegate"),
            new ThreadPoolExecutor.CallerRunsPolicy()
    );

    /** L4 摘要线程池：计算密集型，core=CPU/2 */
    public static final ThreadPoolExecutor L4_SUMMARY = new ThreadPoolExecutor(
            1, 2,
            30L, TimeUnit.SECONDS,
            new LinkedBlockingQueue<>(8),
            new NamedThreadFactory("l4-summary"),
            new ThreadPoolExecutor.DiscardPolicy()
    );

    static {
        log.info("线程池初始化: tool-exec(core=4,max=8,queue=128) " +
                        "task-delegate(core=2,max=4,queue=32) " +
                        "l4-summary(core=1,max=2,queue=8)");
        // 允许 core 线程超时回收，避免空闲线程长期占用
        TOOL_EXEC.allowCoreThreadTimeOut(true);
        TASK_DELEGATE.allowCoreThreadTimeOut(true);
        L4_SUMMARY.allowCoreThreadTimeOut(true);
    }

    private ThreadPoolConfig() {
        // 工具类，禁止实例化
    }
}
