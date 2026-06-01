package com.codemind.core;

import com.codemind.api.llm.*;
import com.codemind.api.session.SessionContext;
import com.codemind.impl.tool.ToolRegistry;

/**
 * Agent 循环引擎
 * 
 * 实现 Agent 的核心"思考-行动-观察"循环（ReAct 模式）。
 * 
 * 循环流程：
 * 1. 思考 (Think)：将用户输入和上下文发送给 LLM
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
     * 运行 Agent 循环
     * 
     * @param input 用户输入
     * @param context 会话上下文
     * @return 执行结果
     */
    public AgentResult run(String input, SessionContext context) {
        try {
            // 1. 将用户输入添加到上下文
            context.addMessage(Message.user(input));
            
            // 2. 开始 Agent 循环
            for (int iteration = 0; iteration < maxIterations; iteration++) {
                
                // 2.1 获取对话历史
                var history = context.getHistory();
                
                // 2.2 获取所有工具定义
                var tools = toolRegistry.getAllDefinitions();
                
                // 2.3 调用 LLM（带工具）
                LLMResponse response = llmClient.chatWithTools(history, tools);
                
                // 2.4 将 LLM 回复添加到历史
                String assistantContent = response.getContent();
                context.addMessage(Message.assistant(assistantContent));
                
                // 2.5 检查是否有工具调用
                if (response.hasToolCalls()) {
                    for (ToolCall toolCall : response.getToolCalls()) {
                        // 执行工具
                        var result = toolRegistry.execute(toolCall.getName(), toolCall.getArguments());
                        
                        // 将工具结果添加到历史
                        String resultContent = result.isSuccess() 
                            ? result.getOutput() 
                            : "Error: " + result.getError();
                        
                        context.addMessage(Message.tool(resultContent, toolCall.getId()));
                    }
                    // 继续循环，获取 LLM 的下一步响应
                } else {
                    // 没有工具调用，说明 LLM 已经给出了最终回答
                    return AgentResult.success(assistantContent);
                }
            }
            
            // 达到最大迭代次数
            return AgentResult.failure("达到最大迭代次数 " + maxIterations);
            
        } catch (Exception e) {
            return AgentResult.failure("Agent 执行失败: " + e.getMessage());
        }
    }
}