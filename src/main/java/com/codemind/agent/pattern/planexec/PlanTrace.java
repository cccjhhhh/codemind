package com.codemind.agent.pattern.planexec;

import com.codemind.llm.ToolCall;
import com.codemind.tool.ToolResult;

import java.util.List;

/**
 * 执行 trace — 每步执行的完整记录，用于可观测性和调试。
 *
 * <p>PlanTrace 记录一次状态转移的完整信息：</p>
 * <ul>
 *   <li>状态流转：prevState → nextState</li>
 *   <li>工具调用：调用了哪些工具及执行结果</li>
 *   <li>耗时：从开始到结束的毫秒数</li>
 *   <li>错误信息：失败原因和失败模式</li>
 * </ul>
 *
 * <p>输出示例：</p>
 * <pre>
 * [Plan Trace] step-1/PLAN_GENERATE → STEP_EXECUTE | duration=1200ms
 *   | tools=[Read, Grep] | success=true
 * </pre>
 */
public record PlanTrace(

    /** 计划 ID */
    String planId,

    /** 步骤 ID */
    String stepId,

    /** 前一状态 */
    PlanState prevState,

    /** 后一状态 */
    PlanState nextState,

    /** 开始时间（毫秒时间戳） */
    long startTimeMs,

    /** 结束时间（毫秒时间戳） */
    long endTimeMs,

    /** 工具调用列表 */
    List<ToolCall> toolCalls,

    /** 工具执行结果列表 */
    List<ToolResult> toolResults,

    /** 错误信息（如有） */
    String errorMessage,

    /** 失败模式（如有） */
    StepFailureMode failureMode
) {
    /**
     * 创建 trace 记录。
     */
    public static PlanTrace of(String planId, String stepId,
                               PlanState from, PlanState to,
                               List<ToolCall> toolCalls,
                               List<ToolResult> toolResults) {
        return new PlanTrace(planId, stepId, from, to,
            System.currentTimeMillis(), System.currentTimeMillis() + 1,
            toolCalls, toolResults, null, null);
    }

    /**
     * 创建失败 trace 记录。
     */
    public static PlanTrace failed(String planId, String stepId,
                                    PlanState from, PlanState to,
                                    String errorMessage,
                                    StepFailureMode failureMode) {
        return new PlanTrace(planId, stepId, from, to,
            System.currentTimeMillis(), System.currentTimeMillis() + 1,
            List.of(), List.of(), errorMessage, failureMode);
    }

    /**
     * 计算耗时（毫秒）。
     */
    public long durationMs() {
        return endTimeMs - startTimeMs;
    }
}
