package com.codemind.core;

import com.codemind.api.llm.LLMClient;
import com.codemind.api.llm.LLMResponse;
import com.codemind.api.session.SessionContext;
import com.codemind.impl.tool.ToolRegistry;

/**
 * Agent 循环引擎
 * 
 * 实现 Agent 的核心"思考-行动-观察"循环。
 * 这是 Agent 应用的核心概念。
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
        // TODO: 实现 Agent 循环
        // 1. 构建初始消息
        // 2. 循环：调用 LLM → 解析响应 → 执行工具 → 收集观察
        // 3. 返回最终结果
        throw new UnsupportedOperationException("Not implemented yet");
    }
}