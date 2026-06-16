package com.codemind.tool.impl;

import com.codemind.tool.spi.Tool;
import com.codemind.tool.ToolResult;
import com.codemind.agent.AgentLoop;
import com.codemind.agent.spi.AgentResult;
import com.codemind.agent.async.TaskDelegationService;
import com.codemind.config.Settings;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.file.Path;
import java.util.Map;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

/**
 * Task 工具：将复杂任务委派给子 Agent 执行。
 *
 * 使用 {@link TaskDelegationService} 的线程池管理子任务执行，
 * 遵循阿里巴巴线程池规范：统一管理、有界队列、可命名线程。
 */
public class TaskTool implements Tool {

    private static final Logger log = LoggerFactory.getLogger(TaskTool.class);
    private static final ObjectMapper JSON = new ObjectMapper();
    private static final int DEFAULT_SUBTASK_TIMEOUT_SECONDS = 300;

    private final TaskDelegationService delegationService;
    private final int subtaskTimeoutSeconds;

    public TaskTool(AgentLoop parentLoop, Path workingDirectory, Settings settings) {
        this.delegationService = new TaskDelegationService(parentLoop, workingDirectory);
        this.subtaskTimeoutSeconds = settings != null && settings.getAgent() != null
            ? settings.getAgent().getSubtaskTimeoutSeconds()
            : DEFAULT_SUBTASK_TIMEOUT_SECONDS;
    }

    @Override
    public String getName() { return "Task"; }

    @Override
    public String getDescription() {
        return "将复杂任务委派给子 Agent 执行。子 Agent 有独立的上下文窗口，只有最终结果会返回。"
             + "适用于：搜索整个项目、并行文件分析、多步调试等。";
    }

    @Override
    public JsonNode getInputSchema() {
        ObjectNode schema = JSON.createObjectNode();
        schema.put("type", "object");
        ObjectNode props = schema.putObject("properties");
        ObjectNode instProp = props.putObject("instruction");
        instProp.put("type", "string");
        instProp.put("description", "子任务的详细指令。说明需要做什么、输出什么格式。");
        schema.putArray("required").add("instruction");
        return schema;
    }

    @Override
    public ToolResult execute(Map<String, Object> params) {
        String instruction = (String) params.get("instruction");
        if (instruction == null || instruction.isBlank()) {
            return ToolResult.failure("参数 'instruction' 是必需的");
        }

        try {
            Future<AgentResult> future = delegationService.delegate(instruction);
            AgentResult result = future.get(subtaskTimeoutSeconds, TimeUnit.SECONDS);

            if (result.isSuccess()) {
                return ToolResult.success(result.getMessage());
            } else {
                return ToolResult.failure("子任务失败: " + result.getError());
            }
        } catch (java.util.concurrent.TimeoutException e) {
            return ToolResult.failure("子任务执行超时(" + subtaskTimeoutSeconds + "s)");
        } catch (Exception e) {
            return ToolResult.failure("子任务异常: " + e.getMessage());
        }
    }

}
