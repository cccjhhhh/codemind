package com.codemind.core;

import com.codemind.api.cli.OutputFormatter;
import com.codemind.api.llm.*;
import com.codemind.api.safety.PermissionGate;
import com.codemind.api.safety.PermissionPrompter;
import com.codemind.api.session.SessionContext;
import com.codemind.api.tool.ToolRegistry;
import com.codemind.api.tool.ToolResult;
import com.codemind.dto.skill.SkillRouteDto;
import com.codemind.impl.safety.SafetyChecker;
import com.codemind.impl.skill.SkillDefinition;
import com.codemind.impl.skill.routing.SkillRouter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;

/**
 * Agent 循环引擎
 *
 * 实现 Agent 的核心"思考-行动-观察"循环（ReAct 模式）。
 * 采用事件驱动的流式输出架构，参考 Claude Code / LangChain 设计。
 *
 * 循环流程：
 * 1. 思考 (Think)：将用户输入和上下文发送给 LLM（流式）
 * 2. 行动 (Act)：如果 LLM 请求工具调用，则执行工具（支持权限确认）
 * 3. 观察 (Observe)：将工具执行结果反馈给 LLM
 * 4. 重复直到 LLM 返回最终回答
 *
 * 重构说明（2026-06-06）：
 * - 方法已拆分为更小的、职责单一的方法
 * - 符合 AI_CODING_STANDARDS.md 中的方法长度限制（≤50行）
 *
 * @see <a href="https://arxiv.org/abs/2210.03629">ReAct 论文</a>
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

    // ==================== 构造方法 ====================

    public AgentLoop(LLMClient llmClient, ToolRegistry toolRegistry,
                      PermissionGate permissionGate, OutputFormatter outputFormatter,
                      int maxIterations) {
        this(llmClient, toolRegistry, permissionGate, outputFormatter, maxIterations, 0);
    }

    @Deprecated
    public AgentLoop(LLMClient llmClient, ToolRegistry toolRegistry,
                      PermissionGate permissionGate, OutputFormatter outputFormatter,
                      int maxIterations, int maxExecutionTimeSeconds) {
        this.llmClient = llmClient;
        this.toolRegistry = toolRegistry;
        this.permissionGate = permissionGate;
        this.outputFormatter = outputFormatter;
        this.maxIterations = maxIterations;
        this.maxExecutionTimeMs = maxExecutionTimeSeconds > 0 ? maxExecutionTimeSeconds * 1000L : 0;
        this.skillRouter = null;
        log.warn("AgentLoop 创建时未提供 SkillRouter，语义路由功能将不可用");
    }

    public AgentLoop(LLMClient llmClient, ToolRegistry toolRegistry,
                      PermissionGate permissionGate, OutputFormatter outputFormatter,
                      int maxIterations, int maxExecutionTimeSeconds,
                      SkillRouter skillRouter) {
        this.llmClient = llmClient;
        this.toolRegistry = toolRegistry;
        this.permissionGate = permissionGate;
        this.outputFormatter = outputFormatter;
        this.maxIterations = maxIterations;
        this.maxExecutionTimeMs = maxExecutionTimeSeconds > 0 ? maxExecutionTimeSeconds * 1000L : 0;
        this.skillRouter = skillRouter;
    }

    // ==================== 公共入口方法 ====================

    public AgentResult runStream(String input, SessionContext context,
                                Consumer<String> outputHandler) {
        try {
            long startTime = System.currentTimeMillis();
            SafetyChecker safetyChecker = new SafetyChecker();

            // 1. 验证输入安全性
            AgentResult safetyResult = validateInput(input, safetyChecker);
            if (safetyResult != null) {
                return safetyResult;
            }

            // 2. 尝试 Skill 路由（仅记录日志，不再执行 — Java 执行器已移除）
            trySkillRouting(input, context, outputHandler);

            // 3. 执行主 Agent 循环
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

    /**
     * 验证用户输入的安全性
     *
     * @return 如果验证失败返回失败结果，null 表示验证通过
     */
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

    /**
     * 尝试 Skill 路由（仅记录日志 — 不再执行 Java 执行器）
     */
    private void trySkillRouting(String input, SessionContext context,
                                 Consumer<String> outputHandler) {
        if (skillRouter == null) {
            return;
        }

        SkillRouteDto route = skillRouter.route(input);
        if (route == null) {
            return;
        }

        if (!route.shouldExecute()) {
            log.debug("Skill route confidence too low: {} (threshold: {})",
                route.confidence(), SkillRouteDto.CONFIDENCE_THRESHOLD);
            return;
        }

        log.info("Skill routed: {} (reason: {}, confidence: {})",
            route.skill().getName(), route.reason(), route.confidence());
        outputHandler.accept(outputFormatter.formatSkillStart(route.skill().getName(), input));
    }

    // ==================== 主循环 ====================

    /**
     * 执行 Agent 主循环
     */
    private AgentResult executeAgentLoop(SessionContext context,
                                        Consumer<String> outputHandler,
                                        SafetyChecker safetyChecker,
                                        long startTime) {
        for (int iteration = 0; iteration < maxIterations; iteration++) {
            // 检查超时
            if (isTimeout(startTime)) {
                outputHandler.accept(outputFormatter.formatWarning(
                    "执行超时（" + (maxExecutionTimeMs / 1000) + "秒），已终止"));
                return AgentResult.failure("执行超时");
            }

            // 显示进度
            if (iteration > 0) {
                long elapsed = System.currentTimeMillis() - startTime;
                outputHandler.accept(outputFormatter.formatProgress(iteration, maxIterations, elapsed));
            }

            // 获取历史和工具
            List<Message> history = context.getManagedHistory();
            List<ToolDefinition> tools = toolRegistry.getAllDefinitions();

            // 显示思考指示器
            outputHandler.accept(outputFormatter.formatThinkingStart());

            // 调用 LLM
            AgentTurnResult turnResult = runSingleTurn(history, tools, outputHandler);

            // 添加 LLM 回复到历史
            addToHistory(context, turnResult);

            // 检查工具调用
            if (turnResult.hasToolCalls()) {
                executeToolCalls(turnResult.toolCalls, context, outputHandler);
            } else {
                return AgentResult.success(safetyChecker.sanitizeOutput(turnResult.fullText));
            }
        }

        return AgentResult.failure("达到最大迭代次数 " + maxIterations);
    }

    /**
     * 检查是否超时
     */
    private boolean isTimeout(long startTime) {
        return maxExecutionTimeMs > 0
            && (System.currentTimeMillis() - startTime) >= maxExecutionTimeMs;
    }

    /**
     * 添加 LLM 回复到历史
     */
    private void addToHistory(SessionContext context, AgentTurnResult turnResult) {
        if (turnResult.hasToolCalls()) {
            context.addMessage(Message.assistantWithTools(turnResult.fullText, turnResult.toolCalls));
        } else {
            context.addMessage(Message.assistant(turnResult.fullText));
        }
    }

    // ==================== 工具执行 ====================

    /**
     * 执行工具调用列表
     */
    private void executeToolCalls(List<ToolCall> toolCalls,
                                  SessionContext context,
                                  Consumer<String> outputHandler) {
        for (ToolCall toolCall : toolCalls) {
            ToolResult result = executeSingleTool(toolCall, outputHandler);
            addToolResultToHistory(context, toolCall, result);
        }
    }

    /**
     * 执行单个工具
     */
    private ToolResult executeSingleTool(ToolCall toolCall,
                                        Consumer<String> outputHandler) {
        outputHandler.accept(outputFormatter.formatToolCallStart(
            toolCall.getName(), toolCall.getArguments()));

        ToolResult result = toolRegistry.execute(toolCall.getName(), toolCall.getArguments());

        outputHandler.accept(outputFormatter.formatToolCallEnd(toolCall.getName(), result));

        return result;
    }

    /**
     * 将工具结果添加到历史
     */
    private void addToolResultToHistory(SessionContext context, ToolCall toolCall, ToolResult result) {
        String resultContent = result.isSuccess()
            ? result.getOutput()
            : "Error: " + result.getError();
        context.addMessage(Message.tool(resultContent, toolCall.getId()));
    }

    // ==================== LLM 调用 ====================

    /**
     * 执行单轮 LLM 调用（流式）
     */
    private AgentTurnResult runSingleTurn(List<Message> history, List<ToolDefinition> tools,
                                          Consumer<String> outputHandler) {
        CountDownLatch latch = new CountDownLatch(1);
        AtomicReference<String> fullText = new AtomicReference<>("");
        AtomicReference<List<ToolCall>> toolCalls = new AtomicReference<>(new ArrayList<>());
        AtomicReference<Exception> error = new AtomicReference<>(null);
        AtomicReference<Boolean> firstDelta = new AtomicReference<>(true);

        llmClient.chatStreamWithTools(history, tools, new LLMClient.StreamHandler() {

            @Override
            public void onEvent(StreamEvent event) {
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
                error.set(e);
                latch.countDown();
            }
        });

        try {
            latch.await();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new RuntimeException("流式输出被中断", e);
        }

        if (error.get() != null) {
            log.error("LLM 调用失败: {} - {}", error.get().getClass().getName(), error.get().getMessage());
            if (error.get().getCause() != null) {
                log.error("LLM 调用失败原因: {}", error.get().getCause().getMessage());
            }
            throw new RuntimeException("LLM 调用失败", error.get());
        }

        return new AgentTurnResult(fullText.get(), toolCalls.get());
    }

    // ==================== 辅助方法 ====================

    // extractReplyMessage 方法已移除
    // 现在直接将 Skill 的输出返回给用户，不再需要 JSON 解析

    // ==================== 内部类 ====================

    /**
     * 单轮 LLM 调用结果
     */
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
