package com.codemind.agent.engine;

import com.codemind.agent.recovery.RecoveryManager;
import com.codemind.llm.ToolCall;
import com.codemind.safety.SafetyChecker;
import com.codemind.session.SessionContext;

import java.util.List;
import java.util.function.Consumer;

/**
 * 执行状态 — 每轮 Agent 状态机迭代的上下文。
 *
 * <p>WorkflowOrchestrator 创建实例、注入依赖，Handler 读写此对象完成状态传递。</p>
 *
 * <p>字段均为包级可写，保持 Handler 实现的简洁性。</p>
 */
public class ExecutionState {

    /** 会话上下文（消息历史、变量等） */
    public final SessionContext sessionContext;

    /** 输出流处理器 */
    public final Consumer<String> outputHandler;

    /** 请求开始时间（毫秒） */
    public final long startTime;

    /** 安全检查器（用于 COMPLETE 终止态的输出消毒） */
    public final SafetyChecker safetyChecker;

    /** 恢复管理器（跨迭代状态） */
    public final RecoveryManager recoveryManager;

    /** THINK 阶段选定的待执行工具列表 */
    public List<ToolCall> pendingToolCalls;

    /** 已完成的完整迭代次数（THINK→ACT 算一次） */
    public int iterationCount = 0;

    public ExecutionState(SessionContext sessionContext,
                          Consumer<String> outputHandler,
                          long startTime,
                          SafetyChecker safetyChecker) {
        this.sessionContext = sessionContext;
        this.outputHandler = outputHandler;
        this.startTime = startTime;
        this.safetyChecker = safetyChecker;
        this.recoveryManager = new RecoveryManager();
    }
}
