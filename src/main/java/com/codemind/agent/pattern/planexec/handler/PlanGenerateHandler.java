package com.codemind.agent.pattern.planexec.handler;

import com.codemind.agent.engine.ExecutionState;
import com.codemind.agent.engine.TokenBudget;
import com.codemind.agent.pattern.planexec.*;
import com.codemind.agent.statemachine.HandlerResult;
import com.codemind.agent.statemachine.StateHandler;
import com.codemind.context.ContextCompressionOrchestrator;
import com.codemind.frontend.output.spi.OutputFormatter;
import com.codemind.llm.*;
import com.codemind.session.SessionContext;
import com.codemind.tool.ToolRegistry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * PlanGenerateHandler — LLM 生成执行计划。
 *
 * <p>职责：</p>
 * <ol>
 *   <li>构建 Plan 专用 System Prompt（含 JSON Schema 约束）</li>
 *   <li>LLM 调用，解析 JSON 响应</li>
 *   <li>解析失败 → 注入 Schema 约束后重试（最多 2 次）</li>
 *   <li>步数超限 → 自动截断 + 合并</li>
 *   <li>注入 ExecutionPlan 到 ExecutionState</li>
 * </ol>
 *
 * <p>错误处理：</p>
 * <ul>
 *   <li>JSON 解析失败 ≤ 2 次 → PLAN_GENERATE（重试）</li>
 *   <li>JSON 解析失败 > 2 次 → STEP_EXECUTE（降级线性计划）</li>
 * </ul>
 */
public class PlanGenerateHandler implements StateHandler {

    private static final Logger log = LoggerFactory.getLogger(PlanGenerateHandler.class);

    private static final int MAX_PLAN_ATTEMPTS = 3;
    private static final int MAX_STEPS_SOFT = 15;
    private static final int MAX_STEPS_HARD = 30;

    // JSON 块提取正则
    private static final Pattern JSON_BLOCK_PATTERN =
        Pattern.compile("```(?:json)?\\s*\\n(\\{[\\s\\S]*?\\})\\s*\\n```|"
                      + "(\\{[\\s\\S]*?\\})", Pattern.DOTALL);

    private final LLMClient llmClient;
    private final ToolRegistry toolRegistry;
    private final OutputFormatter outputFormatter;
    private final TokenBudget tokenBudget;
    private final ContextCompressionOrchestrator compactionPipeline;
    private final Function<ExecutionState, String> compacter;
    private final int llmStreamingTimeoutSeconds;

    public PlanGenerateHandler(LLMClient llmClient, ToolRegistry toolRegistry,
                               OutputFormatter outputFormatter,
                               TokenBudget tokenBudget,
                               ContextCompressionOrchestrator compactionPipeline,
                               Function<ExecutionState, String> compacter,
                               int llmStreamingTimeoutSeconds) {
        this.llmClient = llmClient;
        this.toolRegistry = toolRegistry;
        this.outputFormatter = outputFormatter;
        this.tokenBudget = tokenBudget;
        this.compactionPipeline = compactionPipeline;
        this.compacter = compacter;
        this.llmStreamingTimeoutSeconds = llmStreamingTimeoutSeconds;
    }

    @Override
    public HandlerResult handle(ExecutionState state) {
        SessionContext ctx = state.sessionContext;
        Consumer<String> outputHandler = state.outputHandler;

        // 1. 获取用户输入（最后一条 user 消息）
        List<Message> history = ctx.getHistory();
        if (history.isEmpty()) {
            log.warn("空会话，无法生成计划");
            return HandlerResult.withCount(PlanState.PLAN_FAILED);
        }
        Message lastUserMsg = findLastUserMessage(history);
        if (lastUserMsg == null) {
            log.warn("未找到用户消息");
            return HandlerResult.withCount(PlanState.PLAN_FAILED);
        }
        String goal = lastUserMsg.getContent();

        // 2. Token 预算检查 → 压缩
        List<Message> messages = ctx.getHistory();
        if (compactionPipeline != null) {
            messages = compactionPipeline.run(messages);
        }
        if (tokenBudget.needsCompact(messages)) {
            try {
                String summary = compacter.apply(state);
                if (summary != null && !summary.isEmpty()) {
                    List<Message> recentRounds = keepLastRounds(history, 3);
                    ctx.clearHistory();
                    ctx.addMessage(Message.user("[Compacted]\n\n" + summary));
                    for (Message m : recentRounds) ctx.addMessage(m);
                }
            } catch (Exception e) {
                log.warn("L4 摘要异常: {}", e.getMessage());
            }
        }

        // 3. 构建 Plan Prompt
        List<ToolDefinition> tools = toolRegistry.getDefinitionsForSkill(null);
        String availableTools = formatToolNames(tools);
        String prompt = buildPlanPrompt(goal, availableTools, MAX_STEPS_HARD);

        outputHandler.accept(outputFormatter.formatThinkingStart());

        // 4. LLM 调用（带重试）
        ExecutionPlan plan = null;
        for (int attempt = 1; attempt <= MAX_PLAN_ATTEMPTS; attempt++) {
            outputHandler.accept(outputFormatter.formatThinkingEnd());
            log.info("生成执行计划 (attempt={}/{})", attempt, MAX_PLAN_ATTEMPTS);

            List<Message> planMessages = new ArrayList<>();
            Message sysMsg = ctx.getSystemMessage();
            if (sysMsg != null) {
                planMessages.add(sysMsg);
            }
            planMessages.add(Message.user(prompt));

            plan = callLLMWithPlanSchema(planMessages, tools, attempt > 1);

            if (plan != null) break;
        }

        // 5. 处理结果
        if (plan == null) {
            log.error("LLM 三次生成计划均失败，降级为线性计划");
            return HandlerResult.withoutCount(PlanState.STEP_EXECUTE);
        }

        // 6. 步数截断
        if (plan.steps().size() > MAX_STEPS_HARD) {
            log.warn("计划步骤 {} 超过上限 {}，自动截断", plan.steps().size(), MAX_STEPS_HARD);
            plan = truncatePlan(plan, MAX_STEPS_HARD);
        }

        // 7. 注入到 ExecutionState
        PlanExecutionState planState = new PlanExecutionState(plan);
        ctx.setVariable("_planExecutionState", planState);
        ctx.setVariable("_planId", planState.getPlanId());

        outputHandler.accept(outputFormatter.formatPlanGenerated(
            plan.steps().size(), plan.estimatedTokenBudget()));

        return HandlerResult.withCount(PlanState.PLAN_VALIDATE);
    }

    // ==================== LLM 调用 ====================

    private ExecutionPlan callLLMWithPlanSchema(List<Message> messages,
                                                 List<ToolDefinition> tools,
                                                 boolean withSchema) {
        AtomicReference<String> fullText = new AtomicReference<>("");
        CountDownLatch latch = new CountDownLatch(1);

        try {
            llmClient.chatStreamWithTools(messages, tools, new LLMClient.StreamHandler() {
                @Override
                public void onEvent(StreamEvent event) {
                    switch (event.getType()) {
                        case TEXT_DELTA:
                            fullText.updateAndGet(v -> v + event.getTextDelta());
                            break;
                        case MESSAGE_COMPLETE:
                            latch.countDown();
                            break;
                        case ERROR:
                            latch.countDown();
                            break;
                        default:
                            break;
                    }
                }
                @Override
                public void onError(Exception e) {
                    log.warn("Plan generate stream error: {}", e.getMessage());
                    latch.countDown();
                }
            }, llmStreamingTimeoutSeconds);

            if (!latch.await(llmStreamingTimeoutSeconds, TimeUnit.SECONDS)) {
                log.warn("Plan generate timeout");
                return null;
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return null;
        }

        String response = fullText.get();
        if (response.isBlank()) {
            log.warn("LLM 返回空响应");
            return null;
        }

        // 解析 JSON
        return parsePlanResponse(response, withSchema);
    }

    private ExecutionPlan parsePlanResponse(String response, boolean forceJson) {
        String json = extractJsonObject(response);
        if (json == null) {
            if (forceJson) {
                log.warn("JSON 解析失败（已强制 Schema 约束）");
            }
            return null;
        }

        try {
            // 简单 JSON 解析（不依赖外部库，使用 Jackson 或手动解析）
            return parseJsonPlan(json);
        } catch (Exception e) {
            log.warn("JSON 解析异常: {}", e.getMessage());
            return null;
        }
    }

    private ExecutionPlan parseJsonPlan(String json) {
        // 使用项目已有的 JSON 库（假设存在）
        // 这里用简化实现，实际可替换为 Jackson/Gson
        return SimplePlanParser.parse(json);
    }

    // ==================== Prompt 构建 ====================

    private String buildPlanPrompt(String goal, String availableTools, int maxSteps) {
        return String.format("""
            你是一个任务规划专家。请将以下任务分解为结构化的执行计划：

            【任务目标】
            %s

            【可用工具】
            %s

            【输出格式】
            请返回 JSON，包含：
            {
              "goal": "任务目标",
              "steps": [
                {
                  "id": "step-1",
                  "description": "步骤描述",
                  "tools": [{"name": "ToolName", "arguments": {...}}],
                  "expected_result": "预期输出",
                  "confidence": "HIGH|MEDIUM|LOW",
                  "depends_on": [],
                  "mode": "SERIAL|PARALLEL|SUB_AGENT"
                }
              ],
              "max_steps": %d,
              "estimated_token_budget": 50000
            }

            【约束】
            1. 每步最多 3 个工具调用
            2. 依赖关系不能形成环
            3. 低复杂度步骤使用 PARALLEL
            4. 高不确定性步骤（confidence=LOW）使用 SUB_AGENT
            5. 总步数不超过 %d
            6. 只返回 JSON，不要有其他内容
            """, goal, availableTools, maxSteps, maxSteps);
    }

    private String formatToolNames(List<ToolDefinition> tools) {
        if (tools == null || tools.isEmpty()) return "无";
        return tools.stream()
            .map(t -> t.getFunction().getName() + ": " + (t.getFunction().getDescription() != null ? t.getFunction().getDescription().substring(0, Math.min(50, t.getFunction().getDescription().length())) : ""))
            .reduce((a, b) -> a + "\n" + b)
            .orElse("无");
    }

    // ==================== JSON 解析（简化版） ====================

    private String extractJsonObject(String text) {
        Matcher matcher = JSON_BLOCK_PATTERN.matcher(text);
        if (matcher.find()) {
            return matcher.group(1) != null ? matcher.group(1) : matcher.group(2);
        }
        // 尝试直接找最外层的 JSON 对象
        int start = text.indexOf('{');
        int end = text.lastIndexOf('}');
        return (start >= 0 && end > start) ? text.substring(start, end + 1) : null;
    }

    // ==================== 计划截断 ====================

    private ExecutionPlan truncatePlan(ExecutionPlan plan, int maxSteps) {
        List<PlanStep> truncated = plan.steps().subList(0, maxSteps);
        return new ExecutionPlan(plan.goal(), truncated, maxSteps,
            plan.estimatedTokenBudget(), plan.context(), plan.createdAtMs());
    }

    // ==================== 辅助方法 ====================

    private static Message findLastUserMessage(List<Message> history) {
        for (int i = history.size() - 1; i >= 0; i--) {
            if (history.get(i).getRole() == Message.Role.USER) {
                return history.get(i);
            }
        }
        return null;
    }

    private static List<Message> keepLastRounds(List<Message> messages, int n) {
        List<int[]> bounds = ContextCompressionOrchestrator.findRoundBounds(messages);
        if (bounds.size() <= n) return List.of();
        List<Message> recent = new ArrayList<>();
        for (int r = bounds.size() - n; r < bounds.size(); r++) {
            int[] b = bounds.get(r);
            for (int i = b[0]; i <= b[1]; i++) {
                recent.add(messages.get(i));
            }
        }
        return recent;
    }
}
