package com.codemind.agent.pattern.planexec;

import java.util.ArrayList;
import java.util.List;

/**
 * 计划执行状态 — 跨步骤的共享状态，存储在 {@link com.codemind.agent.engine.ExecutionState} 中。
 *
 * <p>管理 Plan-and-Execute 范式执行过程中的全局状态：</p>
 * <ul>
 *   <li>当前执行到的步骤索引</li>
 *   <li>已完成/已失败的步骤结果</li>
 *   <li>连续失败次数（触发重规划的阈值）</li>
 *   <li>步骤输出缓存（用于依赖解析）</li>
 * </ul>
 *
 * <p>可变对象，由 StepExecuteHandler 和 ReplanHandler 写入。</p>
 */
public class PlanExecutionState {

    /** 当前执行计划 */
    private ExecutionPlan plan;

    /** 当前执行到的步骤索引 */
    private int currentStepIndex;

    /** 已完成的步骤结果 */
    private final List<StepResult> completedSteps = new ArrayList<>();

    /** 已失败的步骤结果 */
    private final List<StepResult> failedSteps = new ArrayList<>();

    /** 连续失败次数（用于触发 LOCAL_REPLAN） */
    private int consecutiveFailures;

    /** 计划开始时间（毫秒） */
    private final long startTimeMs;

    /** 步骤输出缓存（stepId → actualOutput），用于依赖解析 */
    private final java.util.Map<String, String> stepOutputs = new java.util.LinkedHashMap<>();

    /** 计划唯一 ID（用于 trace） */
    private final String planId;

    public PlanExecutionState(ExecutionPlan plan) {
        this.plan = plan;
        this.currentStepIndex = 0;
        this.startTimeMs = System.currentTimeMillis();
        this.planId = "plan-" + java.util.UUID.randomUUID().toString().substring(0, 8);
    }

    // ==================== 步骤导航 ====================

    /** 获取当前步骤 */
    public PlanStep currentStep() {
        if (plan == null || plan.steps() == null) return null;
        if (currentStepIndex < 0 || currentStepIndex >= plan.steps().size()) return null;
        return plan.steps().get(currentStepIndex);
    }

    /** 移动到下一步 */
    public void advance() {
        currentStepIndex++;
    }

    /** 重置到指定步骤（重规划后使用） */
    public void resetTo(int index) {
        this.currentStepIndex = index;
        this.consecutiveFailures = 0;
    }

    /** 是否还有下一步 */
    public boolean hasNext() {
        return plan != null
            && plan.steps() != null
            && currentStepIndex < plan.steps().size();
    }

    // ==================== 结果记录 ====================

    public void addCompletedStep(StepResult result) {
        completedSteps.add(result);
        if (result.actualOutput() != null) {
            stepOutputs.put(result.stepId(), result.actualOutput());
        }
        consecutiveFailures = 0;
    }

    public void addFailedStep(StepResult result) {
        failedSteps.add(result);
        consecutiveFailures++;
    }

    // ==================== 重规划 ====================

    public void updatePlan(ExecutionPlan newPlan) {
        this.plan = newPlan;
        this.currentStepIndex = 0;
        this.consecutiveFailures = 0;
    }

    // ==================== 查询 ====================

    public int getCompletedCount() { return completedSteps.size(); }
    public int getFailedCount() { return failedSteps.size(); }
    public List<StepResult> getCompletedSteps() { return completedSteps; }
    public List<StepResult> getFailedSteps() { return failedSteps; }
    public int getConsecutiveFailures() { return consecutiveFailures; }
    public void setConsecutiveFailures(int n) { this.consecutiveFailures = n; }
    public long getElapsedMs() { return System.currentTimeMillis() - startTimeMs; }
    public String getPlanId() { return planId; }
    public int getCurrentStepIndex() { return currentStepIndex; }
    public void setStepOutputs(java.util.Map<String, String> outputs) { this.stepOutputs.clear(); this.stepOutputs.putAll(outputs); }
    public ExecutionPlan getPlan() { return plan; }
}
