package com.codemind.agent.async;

import com.codemind.agent.AgentLoop;
import com.codemind.agent.spi.AgentResult;
import com.codemind.session.SessionContext;

import java.nio.file.Path;
import java.util.UUID;
import java.util.concurrent.Future;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

/**
 * Task 委派服务 —— 使用独立线程池执行子 Agent 任务。
 *
 * 替代原有 TaskTool 直接同步创建子 Agent 的方式，
 * 使用 TASK_DELEGATE 线程池统一管理子任务执行。
 */
public class TaskDelegationService {

    private final ThreadPoolExecutor executor;
    private final AgentLoop parentLoop;
    private final Path workingDirectory;

    public TaskDelegationService(AgentLoop parentLoop, Path workingDirectory) {
        this.executor = ThreadPoolConfig.TASK_DELEGATE;
        this.parentLoop = parentLoop;
        this.workingDirectory = workingDirectory;
    }

    /**
     * 委派子任务，在线程池中异步执行。
     *
     * @param instruction 子任务指令
     * @return Future，调用方通过 get(timeout) 控制超时
     */
    public Future<AgentResult> delegate(String instruction) {
        return executor.submit(() -> {
            AgentLoop subAgent = parentLoop.createSubAgent();
            SessionContext subCtx = new SessionContext(UUID.randomUUID().toString());
            subCtx.setWorkingDirectory(workingDirectory);
            subCtx.setSystemMessage("你是 CodeMind 的子 Agent。执行以下任务，只返回最终结果。");
            return subAgent.run(instruction, subCtx);
        });
    }

}
