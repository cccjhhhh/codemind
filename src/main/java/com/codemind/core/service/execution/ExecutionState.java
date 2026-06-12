package com.codemind.core.service.execution;

import com.codemind.api.llm.ToolCall;
import com.codemind.api.session.SessionContext;
import com.codemind.core.RecoveryManager;
import com.codemind.impl.safety.SafetyChecker;
import java.util.List;
import java.util.function.Consumer;

public class ExecutionState {
    public final SessionContext sessionContext;
    public final Consumer<String> outputHandler;
    public final long startTime;
    public final RecoveryManager recoveryManager;
    public final SafetyChecker safetyChecker;
    /** THINK 阶段选定的工具调用，由 ACT 阶段消费 */
    public List<ToolCall> pendingToolCalls;
    public int iterationCount;

    public ExecutionState(SessionContext ctx, Consumer<String> output, long startTime, SafetyChecker safetyChecker) {
        this.sessionContext = ctx;
        this.outputHandler = output;
        this.startTime = startTime;
        this.recoveryManager = new RecoveryManager();
        this.safetyChecker = safetyChecker;
        this.iterationCount = 0;
    }
}
