package com.codemind.agent.pattern.planexec;

/**
 * Plan-and-Execute 范式的工作流转状态。
 *
 * <p>状态分为四个阶段：</p>
 * <ul>
 *   <li><b>规划阶段</b>：{@link #PLAN_GENERATE} → {@link #PLAN_VALIDATE}</li>
 *   <li><b>执行阶段</b>：{@link #STEP_EXECUTE} → {@link #STEP_VERIFY} → {@link #STEP_SKIPPED}</li>
 *   <li><b>恢复阶段</b>：{@link #STEP_RETRY} → {@link #LOCAL_REPLAN} → {@link #GLOBAL_REPLAN} → {@link #SUB_AGENT_OFFLOAD}</li>
 *   <li><b>终止阶段</b>：{@link #PLAN_COMPLETE} / {@link #PLAN_PARTIAL} / {@link #PLAN_FAILED}</li>
 * </ul>
 *
 * <p>与 {@link com.codemind.agent.statemachine.pattern.ReactState} 并列，
 * 通过 {@link PlanAgentPattern} 注册到状态机。</p>
 */
public enum PlanState {

    // ==================== 规划阶段 ====================

    /**
     * LLM 生成执行计划（含 DAG 拓扑排序）。
     * 解析 JSON 后进入 PLAN_VALIDATE。
     */
    PLAN_GENERATE,

    /**
     * 验证计划合法性：循环依赖检测、工具可用性检查、依赖完整性检查。
     * 验证通过 → STEP_EXECUTE；验证失败 → LOCAL_REPLAN。
     */
    PLAN_VALIDATE,

    // ==================== 执行阶段 ====================

    /**
     * 执行当前步骤。内部驱动工具调用，完成后进入 STEP_VERIFY。
     * 前置步骤失败时，跳转到 STEP_SKIPPED。
     */
    STEP_EXECUTE,

    /**
     * 验证步骤输出是否符合 expectedResult。
     * 双重验证：规则匹配 + LLM 语义判断。
     * 验证通过 → STEP_EXECUTE（下一步）或 PLAN_COMPLETE（最后一步）；
     * 验证失败 → LOCAL_REPLAN。
     */
    STEP_VERIFY,

    /**
     * 步骤跳过（依赖失败或工具全部失败）。
     * 记录到 failedSteps，继续执行后续步骤。
     */
    STEP_SKIPPED,

    // ==================== 恢复阶段 ====================

    /**
     * 单步重试（指数退避 1s）。
     * 重试成功 → STEP_VERIFY；重试失败 → STEP_SKIPPED。
     */
    STEP_RETRY,

    /**
     * 局部重规划：从失败点开始重新生成剩余步骤。
     * 适用：失败步骤 < 50%。
     * 重规划成功 → STEP_EXECUTE；失败 → GLOBAL_REPLAN。
     */
    LOCAL_REPLAN,

    /**
     * 全局重规划：全量重新生成计划。
     * 适用：失败步骤 ≥ 50% 或 LOCAL_REPLAN 失败。
     * 重规划成功 → STEP_EXECUTE；失败 → PLAN_PARTIAL。
     */
    GLOBAL_REPLAN,

    /**
     * 卸载到子 Agent（复杂/低信心步骤）。
     * 子 Agent 成功 → STEP_VERIFY；失败 → STEP_RETRY；超时 → LOCAL_REPLAN。
     */
    SUB_AGENT_OFFLOAD,

    /**
     * 上下文压缩（复用 ReAct 的 compaction 机制）。
     * 适用：执行过程中 context 超长。
     */
    COMPACT_CONTEXT,

    // ==================== 终止阶段 ====================

    /**
     * 所有步骤完成且最终验证通过。
     */
    PLAN_COMPLETE,

    /**
     * 部分成功：有失败步骤但整体可用。
     * 返回已完成步骤的结果，不报错。
     */
    PLAN_PARTIAL,

    /**
     * 完全失败：达到最大重试/步数上限。
     */
    PLAN_FAILED
}
