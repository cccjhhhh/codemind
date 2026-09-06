package com.codemind.agent.pattern.planexec.handler;

import com.codemind.agent.engine.ExecutionState;
import com.codemind.agent.pattern.planexec.*;
import com.codemind.agent.statemachine.HandlerResult;
import com.codemind.agent.statemachine.StateHandler;
import com.codemind.frontend.output.spi.OutputFormatter;
import com.codemind.llm.LLMClient;
import com.codemind.llm.*;
import com.codemind.session.SessionContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;

/**
 * StepVerifyHandler — 步骤验证，含双重验证机制。
 *
 * <p>验证策略：</p>
 * <ul>
 *   <li>快速路径：关键词规则匹配（O(n) 字符串匹配）</li>
 *   <li>兜底路径：LLM 语义判断（准确但慢）</li>
 *   <li>柔性处理：语义不一致只记录 warning，不阻断执行</li>
 * </ul>
 *
 * <p>验证通过 → STEP_EXECUTE（下一步）或 PLAN_COMPLETE（最后一步）；
 * 验证失败 → LOCAL_REPLAN。</p>
 */
public class StepVerifyHandler implements StateHandler {

    private static final Logger log = LoggerFactory.getLogger(StepVerifyHandler.class);

    private final LLMClient llmClient;
    private final OutputFormatter outputFormatter;
    private final int semanticMatchMinLength = 20;

    public StepVerifyHandler(LLMClient llmClient, OutputFormatter outputFormatter) {
        this.llmClient = llmClient;
        this.outputFormatter = outputFormatter;
    }

    @Override
    public HandlerResult handle(ExecutionState state) {
        SessionContext ctx = state.sessionContext;
        PlanExecutionState pes = getPlanState(ctx);
        if (pes == null || pes.getPlan() == null) {
            return HandlerResult.withCount(PlanState.PLAN_COMPLETE);
        }

        PlanStep step = pes.currentStep();
        if (step == null) {
            log.info("所有步骤验证完成");
            return HandlerResult.withCount(PlanState.PLAN_COMPLETE);
        }

        StepResult lastResult = pes.getCompletedSteps().isEmpty() ? null
            : pes.getCompletedSteps().get(pes.getCompletedSteps().size() - 1);
        if (lastResult == null || !lastResult.success()) {
            log.warn("最后一步未成功，跳过验证");
            return HandlerResult.withCount(PlanState.STEP_EXECUTE);
        }

        // 如果没有预期输出，直接通过
        if (step.expectedResult() == null || step.expectedResult().isBlank()) {
            return proceedToNext(state, pes);
        }

        // 双重验证
        boolean ruleMatch = containsKeywords(lastResult.actualOutput(), step.expectedResult());
        if (ruleMatch) {
            log.info("步骤 {} 规则验证通过", step.id());
            return proceedToNext(state, pes);
        }

        // 规则匹配失败 → LLM 语义判断
        if (llmClient != null && isLongEnough(lastResult.actualOutput())) {
            boolean semanticMatch = semanticMatch(step.expectedResult(), lastResult.actualOutput());
            if (semanticMatch) {
                log.info("步骤 {} 语义验证通过（规则匹配失败但语义一致）", step.id());
                return proceedToNext(state, pes);
            }
        } else {
            log.warn("步骤 {} 规则匹配失败，跳过语义验证（输出太短）", step.id());
            return proceedToNext(state, pes);
        }

        log.warn("步骤 {} 验证失败，触发 LOCAL_REPLAN", step.id());
        return HandlerResult.withCount(PlanState.LOCAL_REPLAN);
    }

    // ==================== 验证逻辑 ====================

    /**
     * 规则匹配：检查实际输出是否包含预期输出中的关键信息。
     */
    private boolean containsKeywords(String actual, String expected) {
        if (actual == null || expected == null) return false;
        String actualLower = actual.toLowerCase();
        String expectedLower = expected.toLowerCase();

        // 精确包含
        if (actualLower.contains(expectedLower)) return true;

        // 关键词匹配（提取预期中的关键名词/动词）
        List<String> keywords = Arrays.stream(expectedLower.split("[\\s,;：；]+"))
            .filter(w -> w.length() > 2)
            .collect(java.util.stream.Collectors.toList());
        long matchCount = keywords.stream()
            .filter(actualLower::contains)
            .count();
        return matchCount >= Math.min(keywords.size(), 2);
    }

    /**
     * LLM 语义匹配：调用 LLM 判断实际输出是否符合预期。
     */
    private boolean semanticMatch(String expected, String actual) {
        try {
            List<Message> messages = new ArrayList<>();
            messages.add(Message.system("你是一个验证助手。判断实际输出是否符合预期。"));
            messages.add(Message.user(String.format("""
                预期输出：%s
                实际输出：%s
                请回答 YES 或 NO，只返回单词。
                """, expected, actual)));

            LLMResponse response = llmClient.chat(messages);
            if (response == null || response.getContent() == null) return false;
            String answer = response.getContent().trim().toUpperCase();
            return answer.startsWith("YES") || answer.contains("符合") || answer.contains("是");
        } catch (Exception e) {
            log.warn("语义匹配异常: {}", e.getMessage());
            return false;
        }
    }

    private boolean isLongEnough(String text) {
        return text != null && text.length() >= semanticMatchMinLength;
    }

    // ==================== 流转控制 ====================

    private HandlerResult proceedToNext(ExecutionState state, PlanExecutionState pes) {
        if (!pes.hasNext()) {
            log.info("所有步骤验证通过，计划完成");
            outputFormatter.formatPlanComplete(pes.getCompletedSteps().size(), pes.getFailedSteps().size());
            return HandlerResult.withCount(PlanState.PLAN_COMPLETE);
        }
        return HandlerResult.withCount(PlanState.STEP_EXECUTE);
    }

    private PlanExecutionState getPlanState(SessionContext ctx) {
        Object v = ctx.getVariable("_planExecutionState");
        return v instanceof PlanExecutionState ? (PlanExecutionState) v : null;
    }
}
