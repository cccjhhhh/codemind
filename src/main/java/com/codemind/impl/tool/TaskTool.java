package com.codemind.impl.tool;

import com.codemind.api.safety.PermissionLevel;
import com.codemind.api.session.SessionContext;
import com.codemind.api.tool.Tool;
import com.codemind.api.tool.ToolResult;
import com.codemind.core.AgentLoop;
import com.codemind.core.AgentResult;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.nio.file.Path;
import java.util.Map;
import java.util.UUID;

public class TaskTool implements Tool {

    private static final ObjectMapper JSON = new ObjectMapper();
    private final AgentLoop parentLoop;
    private final Path workingDirectory;

    public TaskTool(AgentLoop parentLoop, Path workingDirectory) {
        this.parentLoop = parentLoop;
        this.workingDirectory = workingDirectory;
    }

    @Override
    public String getName() { return "Task"; }

    @Override
    public String getDescription() {
        return "将复杂任务委派给子 Agent 执行。子 Agent 有独立的上下文窗口，只有最终结果会返回。"
             + "适用于：搜索整个项目、并行文件分析、多步调试等。";
    }

    @Override
    public PermissionLevel getDefaultPermission() { return PermissionLevel.ALLOW; }

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
            AgentLoop subAgent = parentLoop.createSubAgent();
            SessionContext subContext = new SessionContext(UUID.randomUUID().toString());
            subContext.setWorkingDirectory(workingDirectory);
            subContext.setSystemMessage("你是 CodeMind 的子 Agent。执行以下任务，只返回最终结果。");

            AgentResult result = subAgent.run(instruction, subContext);

            if (result.isSuccess()) {
                return ToolResult.success(result.getMessage());
            } else {
                return ToolResult.failure("子任务失败: " + result.getError());
            }
        } catch (Exception e) {
            return ToolResult.failure("子任务异常: " + e.getMessage());
        }
    }
}
