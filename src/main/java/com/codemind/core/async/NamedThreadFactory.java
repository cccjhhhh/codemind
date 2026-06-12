package com.codemind.core.async;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.concurrent.ThreadFactory;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * 可命名的线程工厂。
 *
 * 【强制】阿里巴巴 Java 开发手册：创建线程池时必须指定有业务含义的名称。
 *
 * 命名格式: {业务含义}-{序号}
 * 例如: tool-exec-1, task-delegate-3, l4-summary-2
 */
public class NamedThreadFactory implements ThreadFactory {

    private static final Logger log = LoggerFactory.getLogger(NamedThreadFactory.class);

    private final String namePrefix;
    private final AtomicInteger counter = new AtomicInteger(1);
    private final boolean daemon;

    public NamedThreadFactory(String namePrefix) {
        this(namePrefix, false);
    }

    public NamedThreadFactory(String namePrefix, boolean daemon) {
        this.namePrefix = namePrefix;
        this.daemon = daemon;
    }

    @Override
    public Thread newThread(Runnable r) {
        Thread t = new Thread(r, namePrefix + "-" + counter.getAndIncrement());
        t.setDaemon(daemon);
        t.setUncaughtExceptionHandler((th, e) ->
            log.error("线程池 [{}] 未捕获异常: {}", namePrefix, e.getMessage(), e));
        return t;
    }

}
