package com.codemind.core;

import com.codemind.api.cli.OutputFormatter;
import com.codemind.api.llm.*;
import com.codemind.api.safety.Permission;
import com.codemind.api.safety.PermissionDecision;
import com.codemind.api.safety.PermissionGate;
import com.codemind.api.safety.PermissionPrompter;
import com.codemind.api.session.SessionContext;
import com.codemind.api.skill.SkillContext;
import com.codemind.api.skill.SkillResult;
import com.codemind.api.tool.ToolRegistry;
import com.codemind.api.tool.ToolResult;
import com.codemind.impl.skill.SkillDefinition;
import com.codemind.impl.skill.SkillRoute;
import com.codemind.impl.skill.SkillRouter;
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
 * 输出格式化：
 * - 使用 OutputFormatter 接口格式化工具调用、Skill 调用、权限请求
 * - 提供清晰的视觉层次，便于用户理解 Agent 行为
 */
public class AgentLoop {
    
    private static final Logger log = LoggerFactory.getLogger(AgentLoop.class);
    
    private final LLMClient llmClient;
    private final ToolRegistry toolRegistry;
    private final PermissionGate permissionGate;
    private final OutputFormatter outputFormatter;
    private final int maxIterations;
    private final long maxExecutionTimeMs;  // 最大执行时间（毫秒）
    private final SkillRouter skillRouter;  // Skill 路由（可选）
    
    /**
     * 创建 AgentLoop
     * 
     * @param llmClient LLM 客户端
     * @param toolRegistry 工具注册中心
     * @param permissionGate 权限网关
     * @param outputFormatter 输出格式化器
     * @param maxIterations 最大迭代次数
     */
    public AgentLoop(LLMClient llmClient, ToolRegistry toolRegistry, 
                      PermissionGate permissionGate, OutputFormatter outputFormatter, 
                      int maxIterations) {
        this(llmClient, toolRegistry, permissionGate, outputFormatter, maxIterations, 0);
    }
    
    /**
     * 创建 AgentLoop（带超时）
     * 
     * @param llmClient LLM 客户端
     * @param toolRegistry 工具注册中心
     * @param permissionGate 权限网关
     * @param outputFormatter 输出格式化器
     * @param maxIterations 最大迭代次数
     * @param maxExecutionTimeSeconds 最大执行时间（秒），0 表示无限制
     */
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
    }
    
    /**
     * 创建 AgentLoop（带 SkillRouter）
     * 
     * @param llmClient LLM 客户端
     * @param toolRegistry 工具注册中心
     * @param permissionGate 权限网关
     * @param outputFormatter 输出格式化器
     * @param maxIterations 最大迭代次数
     * @param maxExecutionTimeSeconds 最大执行时间（秒），0 表示无限制
     * @param skillRouter Skill 路由器（关键词硬路由）
     */
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
    
    /**
     * 运行 Agent 循环（流式输出，带权限确认）
     * 
     * @param input 用户输入
     * @param context 会话上下文
     * @param outputHandler 输出处理器（实时接收文本 token）
     * @param prompter 权限确认处理器
     * @return 执行结果
     */
    public AgentResult runStream(String input, SessionContext context, 
                                  Consumer<String> outputHandler, PermissionPrompter prompter) {
        try {
            // 0. 记录开始时间
            long startTime = System.currentTimeMillis();
            
            // ==================== 新增：Skill 硬路由检查 ====================
            if (skillRouter != null) {
                SkillRoute route = skillRouter.route(input);
                
                if (route != null) {
                    // 检查是否应该执行 Skill（置信度阈值判断）
                    if (!route.shouldExecute()) {
                        // 置信度不足，fallback 到普通 Chat
                        log.debug("Skill route confidence too low: {} (threshold: {})", 
                            route.confidence(), SkillRoute.CONFIDENCE_THRESHOLD);
                        // 继续原有流程，不执行 Skill
                    } else {
                        // 命中 Skill：强制执行
                        outputHandler.accept(outputFormatter.formatSkillStart(
                            route.skill().getName(), input));
                        
                        SkillDefinition skill = route.skill();
                        
                        // 构建 SkillContext
                        SkillContext skillContext = new SkillContext(
                            context,  // SessionContext
                            skill.getName(),
                            input,
                            toolRegistry
                        );
                        
                        // 执行 Skill
                        SkillResult skillResult = skill.execute(skillContext);
                        
                        outputHandler.accept(outputFormatter.formatSkillEnd(skillResult));
                        
                        // Skill 结果添加到历史
                        context.addMessage(Message.user(input));
                        context.addMessage(Message.skillResult(skillResult));
                        
                        // 检查 SkillResult 是否包含 action: reply_to_user
                        // 如果是，直接提取消息返回给用户，不再调用 LLM
                        String replyMessage = extractReplyMessage(skillResult);
                        if (replyMessage != null) {
                            return AgentResult.success(replyMessage);
                        }
                        
                        // 继续原有流程
                        return runLLMWithSkillResult(skillResult, context, outputHandler, prompter, startTime);
                    }
                }
            }
            // ==================== 结束 Skill 路由检查 ====================
            
            // 1. 将用户输入添加到上下文
            context.addMessage(Message.user(input));
            
            // 2. 开始 Agent 循环
            for (int iteration = 0; iteration < maxIterations; iteration++) {
                
                // 2.0 检查超时（参考 LangChain AgentExecutor）
                long elapsed = System.currentTimeMillis() - startTime;
                
                if (maxExecutionTimeMs > 0) {
                    if (elapsed >= maxExecutionTimeMs) {
                        outputHandler.accept(outputFormatter.formatWarning(
                            "执行超时（" + (maxExecutionTimeMs / 1000) + "秒），已终止"));
                        return AgentResult.failure("执行超时");
                    }
                }
                
                // 显示进度（iteration + elapsed time）
                if (iteration > 0) {
                    outputHandler.accept(outputFormatter.formatProgress(iteration, maxIterations, elapsed));
                }
                
                // 2.1 获取经过窗口管理的消息历史和工具定义
                // 学习要点：上下文窗口管理策略的应用
                List<Message> history = context.getManagedHistory();
                List<ToolDefinition> tools = toolRegistry.getAllDefinitions();
                
                // 2.2 显示思考指示器
                outputHandler.accept(outputFormatter.formatThinkingStart());
                
                // 2.3 调用 LLM（流式）- 思考指示器在第一个文本增量时自动清除
                AgentTurnResult turnResult = runSingleTurn(history, tools, outputHandler);
                
                // 2.4 将 LLM 回复添加到历史（包含 tool_calls）
                if (turnResult.hasToolCalls()) {
                    context.addMessage(Message.assistantWithTools(turnResult.fullText, turnResult.toolCalls));
                } else {
                    context.addMessage(Message.assistant(turnResult.fullText));
                }
                
                // 2.4 检查是否有工具调用
                if (turnResult.hasToolCalls()) {
                    // 执行所有工具
                    for (ToolCall toolCall : turnResult.toolCalls) {
                        
                        // 格式化并显示工具调用开始
                        outputHandler.accept(outputFormatter.formatToolCallStart(
                            toolCall.getName(), toolCall.getArguments()));
                        
                        // 执行工具
                        ToolResult result = toolRegistry.execute(
                            toolCall.getName(), 
                            toolCall.getArguments()
                        );
                        
                        // 处理权限确认
                        if (result.needsConfirmation()) {
                            Permission permission = result.getRequiredPermission();
                            String ctx = "工具: " + toolCall.getName();
                            
                            // 调用 prompter 询问用户
                            PermissionDecision decision = prompter.prompt(permission, ctx);
                            
                            switch (decision) {
                                case ALLOW:
                                    // 一次性允许：临时授权，执行后撤销
                                    permissionGate.grantSessionPermission(permission);
                                    result = toolRegistry.execute(toolCall.getName(), toolCall.getArguments());
                                    // 执行完后撤销一次性授权
                                    permissionGate.revokeSessionPermission(permission);
                                    break;
                                case ALLOW_SESSION:
                                    // 会话期间允许：授权后保持
                                    permissionGate.grantSessionPermission(permission);
                                    result = toolRegistry.execute(toolCall.getName(), toolCall.getArguments());
                                    break;
                                case DENY:
                                default:
                                    result = ToolResult.failure("用户拒绝授权: " + permission.getDescription());
                                    break;
                            }
                        }
                        
                        // 格式化并显示工具调用结束
                        outputHandler.accept(outputFormatter.formatToolCallEnd(
                            toolCall.getName(), result));
                        
                        // 将工具结果添加到历史
                        String resultContent = result.isSuccess() 
                            ? result.getOutput() 
                            : "Error: " + result.getError();
                        
                        context.addMessage(Message.tool(resultContent, toolCall.getId()));
                    }
                    // 继续循环（等待下一轮 LLM 响应）
                } else {
                    // 没有工具调用，LLM 已给出最终回答
                    return AgentResult.success(turnResult.fullText);
                }
            }
            
            // 达到最大迭代次数
            return AgentResult.failure("达到最大迭代次数 " + maxIterations);
            
        } catch (Exception e) {
            return AgentResult.failure("Agent 执行失败: " + e.getMessage());
        }
    }
    
    /**
     * 运行 Agent 循环（流式输出，无权限确认）
     * 向后兼容
     */
    public AgentResult runStream(String input, SessionContext context, 
                                  Consumer<String> outputHandler) {
        // 使用一个默认的 PermissionPrompter，总是拒绝危险操作
        return runStream(input, context, outputHandler, (permission, ctx) -> PermissionDecision.DENY);
    }
    
    /**
     * 运行 Agent 循环（同步，无流式输出）
     * 保留向后兼容
     */
    public AgentResult run(String input, SessionContext context) {
        return runStream(input, context, token -> {});  // 空处理器，丢弃输出
    }
    
    /**
     * 执行单轮 LLM 调用（流式）
     */
    private AgentTurnResult runSingleTurn(List<Message> history, List<ToolDefinition> tools,
                                           Consumer<String> outputHandler) {
        
        // 用于同步等待
        CountDownLatch latch = new CountDownLatch(1);
        AtomicReference<String> fullText = new AtomicReference<>("");
        AtomicReference<List<ToolCall>> toolCalls = new AtomicReference<>(new ArrayList<>());
        AtomicReference<Exception> error = new AtomicReference<>(null);
        AtomicReference<Boolean> firstDelta = new AtomicReference<>(true);
        
        // 调用流式 API
        llmClient.chatStreamWithTools(history, tools, new LLMClient.StreamHandler() {
            
            @Override
            public void onEvent(StreamEvent event) {
                switch (event.getType()) {
                    case TEXT_DELTA:
                        // 第一个文本增量：清除思考指示器
                        if (firstDelta.compareAndSet(true, false)) {
                            outputHandler.accept(outputFormatter.formatThinkingEnd());
                        }
                        // 文本增量，实时输出
                        outputHandler.accept(event.getTextDelta());
                        break;
                    
                    case TOOL_CALL_START:
                        // 工具调用开始（这里只记录，格式化由 runStream 处理）
                        break;
                    
                    case TOOL_CALL_DELTA:
                        // 工具参数增量（通常不需要显示）
                        break;
                    
                    case TOOL_CALL_COMPLETE:
                        // 工具调用完成
                        break;
                    
                    case MESSAGE_COMPLETE:
                        // 消息完成
                        fullText.set(event.getFullText());
                        toolCalls.set(event.getToolCalls() != null ? event.getToolCalls() : new ArrayList<>());
                        latch.countDown();
                        break;
                    
                    case ERROR:
                        error.set(event.getError());
                        latch.countDown();
                        break;
                }
            }
            
            @Override
            public void onError(Exception e) {
                error.set(e);
                latch.countDown();
            }
        });
        
        // 等待流式完成
        try {
            latch.await();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new RuntimeException("流式输出被中断", e);
        }
        
        // 检查错误
        if (error.get() != null) {
            System.err.println("LLM Error details: " + error.get().getClass().getName() + " - " + error.get().getMessage());
            if (error.get().getCause() != null) {
                System.err.println("Cause: " + error.get().getCause().getMessage());
            }
            throw new RuntimeException("LLM 调用失败", error.get());
        }
        
        return new AgentTurnResult(fullText.get(), toolCalls.get());
    }
    
    /**
     * 处理 Skill 结果后继续 LLM 流程
     * 
     * 修复：正确处理工具调用循环，而不是只执行一轮就返回失败
     */
    private AgentResult runLLMWithSkillResult(SkillResult skillResult,
                                             SessionContext context,
                                             Consumer<String> outputHandler, 
                                             PermissionPrompter prompter,
                                             long startTime) {
        try {
            // 迭代处理 LLM 响应和工具调用
            for (int iteration = 0; iteration < maxIterations; iteration++) {
                // 检查超时
                if (maxExecutionTimeMs > 0) {
                    long elapsed = System.currentTimeMillis() - startTime;
                    if (elapsed >= maxExecutionTimeMs) {
                        return AgentResult.failure("执行超时");
                    }
                }
                
                // 获取历史和工具
                List<Message> history = context.getManagedHistory();
                List<ToolDefinition> tools = toolRegistry.getAllDefinitions();
                
                // 调用 LLM 处理 Skill 结果
                AgentTurnResult turnResult = runSingleTurn(history, tools, outputHandler);
                
                // 添加 LLM 回复
                if (turnResult.hasToolCalls()) {
                    context.addMessage(Message.assistantWithTools(turnResult.fullText, turnResult.toolCalls));
                } else {
                    context.addMessage(Message.assistant(turnResult.fullText));
                    // 没有工具调用，LLM 已给出最终回答
                    return AgentResult.success(turnResult.fullText);
                }
                
                // 处理工具调用
                for (ToolCall toolCall : turnResult.toolCalls) {
                    outputHandler.accept(outputFormatter.formatToolCallStart(
                        toolCall.getName(), toolCall.getArguments()));
                    
                    ToolResult result = toolRegistry.execute(toolCall.getName(), toolCall.getArguments());
                    
                    if (result.needsConfirmation()) {
                        Permission permission = result.getRequiredPermission();
                        PermissionDecision decision = prompter.prompt(permission, "工具: " + toolCall.getName());
                        
                        switch (decision) {
                            case ALLOW:
                                permissionGate.grantSessionPermission(permission);
                                result = toolRegistry.execute(toolCall.getName(), toolCall.getArguments());
                                permissionGate.revokeSessionPermission(permission);
                                break;
                            case ALLOW_SESSION:
                                permissionGate.grantSessionPermission(permission);
                                result = toolRegistry.execute(toolCall.getName(), toolCall.getArguments());
                                break;
                            case DENY:
                            default:
                                result = ToolResult.failure("用户拒绝授权: " + permission.getDescription());
                                break;
                        }
                    }
                    
                    outputHandler.accept(outputFormatter.formatToolCallEnd(toolCall.getName(), result));
                    
                    String resultContent = result.isSuccess() ? result.getOutput() : "Error: " + result.getError();
                    context.addMessage(Message.tool(resultContent, toolCall.getId()));
                }
                // 继续循环，等待下一轮 LLM 响应
            }
            
            // 达到最大迭代次数
            return AgentResult.failure("达到最大迭代次数 " + maxIterations);
            
        } catch (Exception e) {
            return AgentResult.failure("Agent 执行失败: " + e.getMessage());
        }
    }
    
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
    
    /**
     * 从 SkillResult 中提取直接回复消息
     * 
     * 如果 SkillResult 包含 action: reply_to_user，则提取消息直接返回
     */
    private String extractReplyMessage(SkillResult skillResult) {
        if (!skillResult.isSuccess() || skillResult.getOutput() == null) {
            return null;
        }
        
        String output = skillResult.getOutput();
        
        // 检查是否包含 action: reply_to_user
        if (!output.contains("\"action\"") || !output.contains("reply_to_user")) {
            return null;
        }
        
        // 提取 message 字段
        try {
            java.util.regex.Pattern messagePattern = java.util.regex.Pattern.compile(
                "\"message\"\\s*:\\s*\"([^\"]+)\"");
            java.util.regex.Matcher matcher = messagePattern.matcher(output);
            if (matcher.find()) {
                return matcher.group(1);
            }
        } catch (Exception e) {
            // 忽略解析错误
        }
        
        return null;
    }
}