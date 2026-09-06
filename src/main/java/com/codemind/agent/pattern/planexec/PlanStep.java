package com.codemind.agent.pattern.planexec;

import com.codemind.llm.ToolCall;

import java.util.List;

/**
 * 单个执行步骤 — 携带元信息用于调度和错误处理。
 *
 * <p>每个 PlanStep 包含：</p>
 * <ul>
 *   <li><b>工具调用列表</b>：本步要执行的工具（可为空 = 纯思考步）</li>
 *   <li><b>预期输出</b>：用于 STEP_VERIFY 阶段的验证</li>
 *   <li><b>信心评估</b>：LLM 自评的执行难度（HIGH/MEDIUM/LOW）</li>
 *   <li><b>依赖关系</b>：前置步骤 ID 列表，形成 DAG 调度</li>
 *   <li><b>重试预算</b>：本步最大重试次数（默认 2）</li>
 *   <li><b>执行模式</b>：SERIAL / PARALLEL / SUB_AGENT</li>
 * </ul>
 *
 * <p>不可变对象（record），跨线程安全。</p>
 */
public record PlanStep(

    /** 步骤唯一标识，如 "step-1", "step-2" */
    String id,

    /** 人类可读描述，如「读取 UserModel.java 关键方法」 */
    String description,

    /** 本步要执行的工具调用列表（可为空 = 纯思考步） */
    List<ToolCall> tools,

    /** 预期输出描述（用于 STEP_VERIFY 验证） */
    String expectedResult,

    /** LLM 自评的执行信心（HIGH/MEDIUM/LOW） */
    StepConfidence confidence,

    /** 依赖的前置步骤 ID 列表（形成 DAG） */
    List<String> dependsOn,

    /** 本步最大重试次数（默认 2） */
    int retryBudget,

    /** 执行模式（SERIAL / PARALLEL / SUB_AGENT） */
    StepMode mode
) {
    /** 默认重试预算 */
    public static final int DEFAULT_RETRY_BUDGET = 2;

    /** 默认执行模式 */
    public static final StepMode DEFAULT_MODE = StepMode.SERIAL;

    /** 默认信心评估 */
    public static final StepConfidence DEFAULT_CONFIDENCE = StepConfidence.MEDIUM;

    /**
     * 创建不带工具和依赖的简单步骤。
     */
    public PlanStep(String id, String description, String expectedResult) {
        this(id, description, List.of(), expectedResult,
             DEFAULT_CONFIDENCE, List.of(), DEFAULT_RETRY_BUDGET, DEFAULT_MODE);
    }
}
