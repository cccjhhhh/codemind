package com.codemind.agent.spi;

import com.codemind.agent.SystemPromptBuilder;
import com.codemind.agent.engine.ExecutionState;
import com.codemind.agent.engine.TokenBudget;
import com.codemind.agent.statemachine.StateHandler;
import com.codemind.frontend.output.spi.OutputFormatter;
import com.codemind.llm.LLMClient;
import com.codemind.session.CompactionPipeline;
import com.codemind.tool.ToolRegistry;

import java.util.Map;
import java.util.function.Function;

/**
 * Agent 模式插件接口。
 *
 * <p>每种 Agent 模式（ReAct、MultiAgent、PlanAndExecute）实现此接口，
 * 通过 {@link #createHandlers} 注册自己的状态处理器，
 * 通过 {@link #initialState()} 指定初始状态。</p>
 *
 * <p>新增模式只需新建一个包 + 实现此接口，{@code WorkflowOrchestrator} 零改动。</p>
 */
public interface AgentPattern {

    /** 模式唯一标识，如 "react", "multiagent", "plan-execute" */
    String name();

    /** 初始状态，如 ReactState.THINK */
    Object initialState();

    /**
     * 创建该模式所需的所有状态处理器。
     *
     * @param llmClient        LLM 客户端
     * @param toolRegistry     工具注册表
     * @param outputFormatter  输出格式化器
     * @param compactionPipeline 上下文压缩管线
     * @param tokenBudget      Token 预算
     * @param promptBuilder     System Prompt 构建器
     * @param compacter        压缩回调（方法引用）
     * @param fileContentCache 文件内容缓存（跨请求 LRU）
     * @param llmStreamingTimeoutSeconds LLM 流式超时
     * @return 状态 → 处理器映射表
     */
    Map<Object, StateHandler> createHandlers(
        LLMClient llmClient,
        ToolRegistry toolRegistry,
        OutputFormatter outputFormatter,
        CompactionPipeline compactionPipeline,
        TokenBudget tokenBudget,
        SystemPromptBuilder promptBuilder,
        Function<ExecutionState, String> compacter,
        Map<String, String> fileContentCache,
        int llmStreamingTimeoutSeconds
    );
}
