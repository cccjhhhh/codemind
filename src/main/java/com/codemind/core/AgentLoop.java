package com.codemind.core;

import com.codemind.api.cli.OutputFormatter;
import com.codemind.api.llm.*;
import com.codemind.api.safety.PermissionGate;
import com.codemind.api.session.SessionContext;
import com.codemind.api.tool.ToolRegistry;
import com.codemind.api.tool.ToolResult;
import com.codemind.dto.skill.SkillRouteDto;
import com.codemind.impl.cli.SystemPromptBuilder;
import com.codemind.impl.safety.SafetyChecker;
import com.codemind.impl.skill.routing.SkillRouter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;

/**
 * Agent 循环引擎
 *
 * 实现 Agent 的核心"思考-行动-观察"循环（ReAct 模式）。
 * 采用事件驱动的流式输出架构。
 *
 * 循环流程：
 * 1. 思考 (Think)：将用户输入和上下文发送给 LLM（流式）
 * 2. 行动 (Act)：如果 LLM 请求工具调用，则执行工具（支持权限确认）
 * 3. 观察 (Observe)：将工具执行结果反馈给 LLM
 * 4. 重复直到 LLM 返回最终回答
 */
public class AgentLoop {

    private static final Logger log = LoggerFactory.getLogger(AgentLoop.class);

    private final LLMClient llmClient;
    private final ToolRegistry toolRegistry;
    private final PermissionGate permissionGate;
    private final OutputFormatter outputFormatter;
    private final int maxIterations;
    private final long maxExecutionTimeMs;
    private final SkillRouter skillRouter;
    private final SystemPromptBuilder promptBuilder;

    public AgentLoop(LLMClient llmClient, ToolRegistry toolRegistry,
                      PermissionGate permissionGate, OutputFormatter outputFormatter,
                      int maxIterations, int maxExecutionTimeSeconds,
                      SkillRouter skillRouter, SystemPromptBuilder promptBuilder) {
        this.llmClient = llmClient;
        this.toolRegistry = toolRegistry;
        this.permissionGate = permissionGate;
        this.outputFormatter = outputFormatter;
        this.maxIterations = maxIterations;
        this.maxExecutionTimeMs = maxExecutionTimeSeconds > 0 ? maxExecutionTimeSeconds * 1000L : 0;
        this.skillRouter = skillRouter;
        this.promptBuilder = promptBuilder;
    }

    // ==================== 公共入口方法 ====================

    public AgentResult runStream(String input, SessionContext context,
                                Consumer<String> outputHandler) {
        try {
            long startTime = System.currentTimeMillis();
            SafetyChecker safetyChecker = new SafetyChecker();

            AgentResult safetyResult = validateInput(input, safetyChecker);
            if (safetyResult != null) {
                return safetyResult;
            }

            if (context.hasActiveSkill()) {
                context.clearActiveSkill();
            }
            tryActivateSkill(input, context);

            context.addMessage(Message.user(input));
            return executeAgentLoop(context, outputHandler, safetyChecker, startTime);

        } catch (Exception e) {
            log.error("Agent 执行异常", e);
            return AgentResult.failure("Agent 执行失败: " + e.getMessage());
        }
    }

    public AgentResult run(String input, SessionContext context) {
        return runStream(input, context, token -> {});
    }

    // ==================== 输入验证 ====================

    private AgentResult validateInput(String input, SafetyChecker safetyChecker) {
        if (!safetyChecker.isInputSafe(input)) {
            log.warn("输入包含不安全内容，已拒绝");
            return AgentResult.failure("输入包含不安全内容，请检查您的输入");
        }

        if (safetyChecker.detectPromptInjection(input)) {
            log.warn("检测到 Prompt 注入尝试，已拒绝");
            return AgentResult.failure("检测到 Prompt 注入尝试，请使用正常方式交流");
        }

        return null;
    }

    // ==================== Skill 路由 ====================

    private boolean tryActivateSkill(String input, SessionContext context) {
        if (skillRouter == null) return false;

        SkillRouteDto route = skillRouter.route(input);
        if (route == null || !route.shouldExecute()) return false;

        context.setActiveSkill(route.skill());
        log.debug("Skill activated: {} (reason: {}, confidence: {})",
            route.skill().getName(), route.reason(), route.confidence());
        return true;
    }

    // ==================== 主循环 ====================

    private AgentResult executeAgentLoop(SessionContext context,
                                        Consumer<String> outputHandler,
                                        SafetyChecker safetyChecker,
                                        long startTime) {
        int consecutiveFailures = 0;

        for (int iteration = 0; iteration < maxIterations; iteration++) {
            if (isTimeout(startTime)) {
                outputHandler.accept(outputFormatter.formatWarning(
                    "执行超时（" + (maxExecutionTimeMs / 1000) + "秒），已终止"));
                return AgentResult.failure("执行超时");
            }

            if (iteration > 0) {
                long elapsed = System.currentTimeMillis() - startTime;
                outputHandler.accept(outputFormatter.formatProgress(iteration, maxIterations, elapsed));
            }

            if (promptBuilder != null) {
                String prompt = promptBuilder.build(context);
                context.setSystemMessage(prompt);
            }

            List<Message> history = context.getManagedHistory();
            // 关键：按 skill.allowedTools 过滤工具，避免 LLM 看到不被允许的工具（如 code_review 没声明 Write 就不会拿到）
            List<ToolDefinition> tools = toolRegistry.getDefinitionsForSkill(context.getActiveSkill());

            outputHandler.accept(outputFormatter.formatThinkingStart());

            AgentTurnResult turnResult = runSingleTurn(history, tools, outputHandler);

            if (!turnResult.hasToolCalls() && turnResult.fullText.isEmpty()) {
                log.warn("LLM 返回空结果，插入重试提示");
                context.addMessage(Message.user("【系统提示】上一轮 LLM 调用失败，请重新尝试。"));
                continue;
            }

            addToHistory(context, turnResult);

            if (turnResult.hasToolCalls()) {
                boolean allFailed = executeToolCalls(turnResult.toolCalls, context, outputHandler);

                if (allFailed) {
                    consecutiveFailures++;
                    if (consecutiveFailures >= 3) {
                        String hint = "【系统提示】连续 " + consecutiveFailures
                            + " 轮工具调用全部失败。请检查最后一条失败原因："
                            + "若提示缺少必需参数，务必检查工具定义并在调用时补全所有 required 字段；"
                            + "若命令报错'找不到路径'说明路径不存在，检查工作目录后用正确路径重试；"
                            + "若被安全策略拒绝则换其他方式。";
                        context.addMessage(Message.user(hint));
                        consecutiveFailures = 0;
                    }
                } else {
                    consecutiveFailures = 0;
                }
            } else {
                return AgentResult.success(safetyChecker.sanitizeOutput(turnResult.fullText));
            }
        }

        return AgentResult.failure("达到最大迭代次数 " + maxIterations);
    }

    private boolean isTimeout(long startTime) {
        return maxExecutionTimeMs > 0
            && (System.currentTimeMillis() - startTime) >= maxExecutionTimeMs;
    }

    private void addToHistory(SessionContext context, AgentTurnResult turnResult) {
        if (turnResult.hasToolCalls()) {
            context.addMessage(Message.assistantWithTools(turnResult.fullText, turnResult.toolCalls));
        } else {
            context.addMessage(Message.assistant(turnResult.fullText));
        }
    }

    // ==================== 工具执行 ====================

    private boolean executeToolCalls(List<ToolCall> toolCalls,
                                     SessionContext context,
                                     Consumer<String> outputHandler) {
        boolean allFailed = true;
        for (ToolCall toolCall : toolCalls) {
            ToolResult result = executeSingleTool(toolCall, outputHandler);
            addToolResultToHistory(context, toolCall, result);
            if (result.isSuccess()) {
                allFailed = false;
            }
        }
        return allFailed;
    }

    private ToolResult executeSingleTool(ToolCall toolCall,
                                        Consumer<String> outputHandler) {
        outputHandler.accept(outputFormatter.formatToolCallStart(
            toolCall.getName(), toolCall.getArguments()));

        ToolResult result = toolRegistry.execute(toolCall.getName(), toolCall.getArguments());

        outputHandler.accept(outputFormatter.formatToolCallEnd(toolCall.getName(), result));

        return result;
    }

    private void addToolResultToHistory(SessionContext context, ToolCall toolCall, ToolResult result) {
        String resultContent = result.isSuccess()
            ? result.getOutput()
            : "Error: " + result.getError();
        context.addMessage(Message.tool(resultContent, toolCall.getId()));
    }

    // ==================== LLM 调用 ====================

    private AgentTurnResult runSingleTurn(List<Message> history, List<ToolDefinition> tools,
                                          Consumer<String> outputHandler) {
        CountDownLatch latch = new CountDownLatch(1);
        AtomicReference<String> fullText = new AtomicReference<>("");
        AtomicReference<List<ToolCall>> toolCalls = new AtomicReference<>(new ArrayList<>());
        AtomicReference<Exception> error = new AtomicReference<>(null);
        AtomicReference<Boolean> firstDelta = new AtomicReference<>(true);
        AtomicBoolean cancelled = new AtomicBoolean(false);

        llmClient.chatStreamWithTools(history, tools, new LLMClient.StreamHandler() {

            @Override
            public void onEvent(StreamEvent event) {
                if (cancelled.get()) return;
                switch (event.getType()) {
                    case TEXT_DELTA:
                        if (firstDelta.compareAndSet(true, false)) {
                            outputHandler.accept(outputFormatter.formatThinkingEnd());
                        }
                        outputHandler.accept(event.getTextDelta());
                        break;
                    case MESSAGE_COMPLETE:
                        fullText.set(event.getFullText());
                        toolCalls.set(event.getToolCalls() != null ? event.getToolCalls() : new ArrayList<>());
                        latch.countDown();
                        break;
                    case ERROR:
                        error.set(event.getError());
                        latch.countDown();
                        break;
                    default:
                        break;
                }
            }

            @Override
            public void onError(Exception e) {
                if (cancelled.get()) return;
                error.set(e);
                latch.countDown();
            }
        });

        try {
            if (!latch.await(30, TimeUnit.SECONDS)) {
                cancelled.set(true);
                log.error("LLM 流式响应超时（30s）");
                return new AgentTurnResult("", new ArrayList<>());
            }
        } catch (InterruptedException e) {
            cancelled.set(true);
            Thread.currentThread().interrupt();
            return new AgentTurnResult("", new ArrayList<>());
        }

        if (error.get() != null) {
            cancelled.set(true);
            log.error("LLM 调用失败: {} - {}", error.get().getClass().getName(), error.get().getMessage());
            if (error.get().getCause() != null) {
                log.error("LLM 调用失败原因: {}", error.get().getCause().getMessage());
            }
            return new AgentTurnResult("", new ArrayList<>());
        }

        return new AgentTurnResult(fullText.get(), toolCalls.get());
    }

    // ==================== 子 Agent 支持 ====================

    /**
     * 创建子 Agent（供 Task 工具使用）。
     * 子 Agent：
     * - 不跑 SkillRouter（避免递归激活 skill）
     * - 共享主 Agent 的 toolRegistry、permissionGate
     * - 最大迭代 15 轮
     * - 超时 60 秒
     */
    public AgentLoop createSubAgent() {
        return new AgentLoop(
            this.llmClient,
            this.toolRegistry,
            this.permissionGate,
            this.outputFormatter,
            15,          // maxIterations（子 Agent 减半）
            60,          // maxExecutionTimeSeconds
            null,        // skillRouter = null（不激活 skill）
            null         // promptBuilder = null（用默认 system message）
        );
    }

    // ==================== 内部类 ====================

    private static class AgentTurnResult {
        final String fullText;
        final List<ToolCall> toolCalls;

        AgentTurnResult(String fullText, List<ToolCall> toolCalls) {
            this.fullText = fullText;
            this.toolCalls = toolCalls;
        }

        boolean hasToolCalls() {
            return toolCalls != null && !toolCalls.isEmpty();
        }
    }
}
