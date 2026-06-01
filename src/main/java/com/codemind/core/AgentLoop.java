package com.codemind.core;

import com.codemind.api.llm.*;
import com.codemind.api.session.SessionContext;
import com.codemind.api.tool.ToolResult;
import com.codemind.impl.tool.ToolRegistry;

import java.util.List;
import java.util.ArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Agent 循环引擎
 * 
 * 实现 Agent 的核心"思考-行动-观察"循环（ReAct 模式）。
 * 采用事件驱动的流式输出架构，参考 Claude Code / LangChain 设计。
 * 
 * 循环流程：
 * 1. 思考 (Think)：将用户输入和上下文发送给 LLM（流式）
 * 2. 行动 (Act)：如果 LLM 请求工具调用，则执行工具
 * 3. 观察 (Observe)：将工具执行结果反馈给 LLM
 * 4. 重复直到 LLM 返回最终回答
 */
public class AgentLoop {
    
    private final LLMClient llmClient;
    private final ToolRegistry toolRegistry;
    private final int maxIterations;
    
    public AgentLoop(LLMClient llmClient, ToolRegistry toolRegistry, int maxIterations) {
        this.llmClient = llmClient;
        this.toolRegistry = toolRegistry;
        this.maxIterations = maxIterations;
    }
    
    /**
     * 运行 Agent 循环（流式输出）
     * 
     * @param input 用户输入
     * @param context 会话上下文
     * @param outputHandler 输出处理器（实时接收文本 token）
     * @return 执行结果
     */
    public AgentResult runStream(String input, SessionContext context, 
                                  java.util.function.Consumer<String> outputHandler) {
        try {
            // 1. 将用户输入添加到上下文
            context.addMessage(Message.user(input));
            
            // 2. 开始 Agent 循环
            for (int iteration = 0; iteration < maxIterations; iteration++) {
                
                // 2.1 获取对话历史和工具定义
                List<Message> history = context.getHistory();
                List<ToolDefinition> tools = toolRegistry.getAllDefinitions();
                
                // 2.2 调用 LLM（流式）
                AgentTurnResult turnResult = runSingleTurn(history, tools, outputHandler);
                
                // 2.3 将 LLM 回复添加到历史（包含 tool_calls）
                if (turnResult.hasToolCalls()) {
                    context.addMessage(Message.assistantWithTools(turnResult.fullText, turnResult.toolCalls));
                } else {
                    context.addMessage(Message.assistant(turnResult.fullText));
                }
                
                // 2.4 检查是否有工具调用
                if (turnResult.hasToolCalls()) {
                    // 执行所有工具
                    for (ToolCall toolCall : turnResult.toolCalls) {
                        // 执行工具
                        ToolResult result = toolRegistry.execute(
                            toolCall.getName(), 
                            toolCall.getArguments()
                        );
                        
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
                                           java.util.function.Consumer<String> outputHandler) {
        
        // 用于同步等待
        CountDownLatch latch = new CountDownLatch(1);
        AtomicReference<String> fullText = new AtomicReference<>("");
        AtomicReference<List<ToolCall>> toolCalls = new AtomicReference<>(new ArrayList<>());
        AtomicReference<Exception> error = new AtomicReference<>(null);
        
        // 调用流式 API
        llmClient.chatStreamWithTools(history, tools, new LLMClient.StreamHandler() {
            
            @Override
            public void onEvent(StreamEvent event) {
                switch (event.getType()) {
                    case TEXT_DELTA:
                        // 文本增量，实时输出
                        outputHandler.accept(event.getTextDelta());
                        break;
                    
                    case TOOL_CALL_START:
                        // 工具调用开始
                        outputHandler.accept("\n[Using " + event.getToolCallName() + "...]");
                        break;
                    
                    case TOOL_CALL_DELTA:
                        // 工具参数增量（通常不需要显示）
                        break;
                    
                    case TOOL_CALL_COMPLETE:
                        // 工具调用完成，添加到列表
                        outputHandler.accept(" done\n");
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