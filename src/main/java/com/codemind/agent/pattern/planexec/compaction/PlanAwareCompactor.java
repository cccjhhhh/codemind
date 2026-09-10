package com.codemind.agent.pattern.planexec.compaction;

import com.codemind.agent.pattern.planexec.ExecutionPlan;
import com.codemind.agent.pattern.planexec.PlanStep;
import com.codemind.llm.Message;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;

/**
 * PlanAwareCompactor — 计划感知的上下文注入器。
 *
 * <p>退化为纯注入器：不做压缩，只注入 step 上下文到消息头。</p>
 *
 * <p>实际压缩由 {@link com.codemind.context.ContextCompressionOrchestrator} 统一处理，
 * 遵守「压缩单入口」规则。</p>
 */
public class PlanAwareCompactor {

    private static final Logger log = LoggerFactory.getLogger(PlanAwareCompactor.class);

    public PlanAwareCompactor() {
        // 无依赖，纯注入
    }

    /**
     * 注入当前步骤上下文到消息头，不做压缩。
     *
     * @param history     当前消息历史
     * @param currentStep 当前执行步骤
     * @param plan        完整执行计划
     * @return 注入后的消息列表（不做任何压缩）
     */
    public List<Message> compact(List<Message> history, PlanStep currentStep, ExecutionPlan plan) {
        if (history == null || history.size() <= 10) return history;
        return injectStepContext(history, currentStep, plan);
    }

    /**
     * 注入当前步骤上下文到消息头。
     */
    private List<Message> injectStepContext(List<Message> history,
                                             PlanStep currentStep, ExecutionPlan plan) {
        if (currentStep == null || plan == null) return history;

        int stepIndex = plan.steps().indexOf(currentStep);
        String expected = currentStep.expectedResult() != null
            ? currentStep.expectedResult() : "(no specific requirement)";
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
