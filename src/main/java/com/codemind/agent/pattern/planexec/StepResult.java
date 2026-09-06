package com.codemind.agent.pattern.planexec;

import com.codemind.llm.ToolCall;

import java.util.List;

/**
 * 步骤执行结果 — 用于验证和错误追踪。
 *
 * <p>StepExecuteHandler 执行完一个 PlanStep 后生成 StepResult，
 * 传递给 StepVerifyHandler 进行验证，同时记录到 PlanExecutionState 中。</p>
 *
 * <p>关键字段：</p>
 * <ul>
 *   <li>{@code errorMessage} — 失败原因，重规划时注入到 Prompt 帮助 LLM 避免重复错误</li>
 *   <li>{@code failureMode} — 失败根因分类，指导恢复策略选择</li>
 *   <li>{@code durationMs} — 执行耗时，用于性能分析和超时决策</li>
 *   <li>{@code retryCount} — 实际重试次数，用于统计和诊断</li>
 * </ul>
 */
public record StepResult(

    /** 关联的步骤 ID */
    String stepId,

    /** 是否成功 */
    boolean success,

    /** 实际输出内容 */
    String actualOutput,

    /** 失败原因（success=false 时非空） */
    String errorMessage,

    /** 执行耗时（毫秒） */
    long durationMs,

    /** 实际重试次数 */
    int retryCount,

    /** 本步调用的工具列表 */
    List<ToolCall> toolCalls,

    /** 失败根因模式（success=false 时非空） */
    StepFailureMode failureMode
) {
    /**
     * 创建成功结果。
     */
    public static StepResult success(String stepId, String output,
                                     List<ToolCall> toolCalls, long durationMs) {
        return new StepResult(stepId, true, output, null, durationMs, 0, toolCalls, null);
    }

    /**
     * 创建失败结果。
     */
    public static StepResult failure(String stepId, String errorMessage,
                                     StepFailureMode failureMode,
                                     List<ToolCall> toolCalls, long durationMs, int retryCount) {
        return new StepResult(stepId, false, null, errorMessage, durationMs, retryCount,
                              toolCalls, failureMode);
    }

    /**
     * 创建跳过结果（依赖失败等）。
     */
    public static StepResult skipped(String stepId, String reason) {
        return new StepResult(stepId, false, null, reason, 0, 0, List.of(),
                              StepFailureMode.DEPENDENCY_FAILED);
    }
}
