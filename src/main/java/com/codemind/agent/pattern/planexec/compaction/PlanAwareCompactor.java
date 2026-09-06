package com.codemind.agent.pattern.planexec.compaction;

import com.codemind.agent.pattern.planexec.ExecutionPlan;
import com.codemind.agent.pattern.planexec.PlanStep;
import com.codemind.context.ContextCompressionOrchestrator;
import com.codemind.llm.Message;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;

/**
 * PlanAwareCompactor — 计划感知的上下文压缩策略。
 *
 * <p>不同于传统的时间窗口压缩，Plan-aware 压缩根据当前执行步骤智能决定保留哪些历史：</p>
 * <ul>
 *   <li>保留当前步骤相关的最近 N 轮完整历史</li>
 *   <li>压缩非当前步骤相关的旧历史为摘要</li>
 *   <li>注入当前步骤上下文到消息头</li>
 * </ul>
 *
 * <p>复用现有的 {@link ContextCompressionOrchestrator} 完成实际压缩。</p>
 */
public class PlanAwareCompactor {

    private static final Logger log = LoggerFactory.getLogger(PlanAwareCompactor.class);
    private static final int KEEP_ROUNDS = 5;

    private final ContextCompressionOrchestrator compactionPipeline;

    public PlanAwareCompactor(ContextCompressionOrchestrator compactionPipeline) {
        this.compactionPipeline = compactionPipeline;
    }

    /**
     * 根据当前执行步骤，决定压缩策略并返回压缩后的消息列表。
     *
     * @param history 当前完整消息历史
     * @param currentStep 当前执行步骤
     * @param plan 完整执行计划
     * @return 压缩后的消息列表
     */
    public List<Message> compact(List<Message> history, PlanStep currentStep, ExecutionPlan plan) {
        if (history.size() <= 10) return history; // 历史太短，不压缩

        // 1. 保留最近 N 轮完整历史
        List<int[]> bounds = ContextCompressionOrchestrator.findRoundBounds(history);
        List<Message> recentHistory = new ArrayList<>();
        int keepFrom = Math.max(0, bounds.size() - KEEP_ROUNDS);
        for (int i = keepFrom; i < bounds.size(); i++) {
            int[] b = bounds.get(i);
            for (int j = b[0]; j <= b[1] && j < history.size(); j++) {
                recentHistory.add(history.get(j));
            }
        }

        // 2. 压缩旧历史
        List<Message> oldHistory = new ArrayList<>();
        for (int i = 0; i < (keepFrom > 0 ? bounds.get(keepFrom - 1)[1] + 1 : 0); i++) {
            oldHistory.add(history.get(i));
        }

        if (oldHistory.size() <= 2) {
            // 旧历史太短，不需要压缩
            return injectStepContext(recentHistory, currentStep, plan);
        }

        try {
            String summary = compactionPipeline.summarize(null, 0, java.util.Collections.emptyMap());
            if (summary != null && !summary.isBlank()) {
                recentHistory.add(0, Message.user("[Compacted History]\n" + summary));
            }
        } catch (Exception e) {
            log.warn("Plan-aware 压缩失败，使用原始历史: {}", e.getMessage());
        }

        return injectStepContext(recentHistory, currentStep, plan);
    }

    /**
     * 注入当前步骤上下文到消息头。
     */
    private List<Message> injectStepContext(List<Message> history,
                                             PlanStep currentStep, ExecutionPlan plan) {
        int stepIndex = plan.steps().indexOf(currentStep);
        String expected = currentStep.expectedResult() != null ? currentStep.expectedResult() : "(no specific requirement)";
        Message contextMsg = Message.user(
            String.format("[Plan Step %d/%d]\n%s\nExpected: %s",
                stepIndex + 1, plan.steps().size(),
                currentStep.description(), expected));

        List<Message> result = new ArrayList<>();
        result.add(contextMsg);
        result.addAll(history);
        return result;
    }
}
