package com.codemind.impl.tool;

import com.codemind.api.safety.PermissionLevel;
import com.codemind.api.tool.Tool;
import com.codemind.api.tool.ToolResult;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;

public class TodoTool implements Tool {

    private static final ObjectMapper JSON = new ObjectMapper();
    private static final String TODO_FILE = ".codemind/todo.json";

    @Override
    public String getName() { return "Todo"; }

    @Override
    public String getDescription() {
        return "管理任务规划列表。支持 init（创建计划）、update（更新状态）、list（查看进度）三种操作。";
    }

    @Override
    public PermissionLevel getDefaultPermission() { return PermissionLevel.ALLOW; }

    @Override
    public JsonNode getInputSchema() {
        ObjectNode schema = JSON.createObjectNode();
        schema.put("type", "object");
        ObjectNode props = schema.putObject("properties");
        ObjectNode opProp = props.putObject("operation");
        opProp.put("type", "string");
        ArrayNode enumValues = opProp.putArray("enum");
        enumValues.add("init");
        enumValues.add("update");
        enumValues.add("list");
        opProp.put("description", "init=创建计划, update=更新状态, list=查看进度");
        props.putObject("goal").put("type", "string");
        props.putObject("items").put("type", "array");
        props.putObject("item_id").put("type", "string");
        props.putObject("status").put("type", "string");
        schema.putArray("required").add("operation");
        return schema;
    }

    @Override
    public ToolResult execute(Map<String, Object> params) {
        String op = (String) params.get("operation");
        Path todoFile = Path.of(TODO_FILE).toAbsolutePath();

        return switch (op) {
            case "init" -> cmdInit(todoFile, params);
            case "update" -> cmdUpdate(todoFile, params);
            case "list" -> cmdList(todoFile);
            default -> ToolResult.failure("未知操作: " + op + "（可选: init, update, list）");
        };
    }

    @SuppressWarnings("unchecked")
    private ToolResult cmdInit(Path todoFile, Map<String, Object> params) {
        try {
            Files.createDirectories(todoFile.getParent());
            Map<String, Object> plan = new LinkedHashMap<>();
            plan.put("goal", params.getOrDefault("goal", "未定义目标"));
            plan.put("status", "in_progress");
            plan.put("items", params.getOrDefault("items", List.of()));
            JSON.writeValue(todoFile.toFile(), plan);
            int itemCount = ((List<?>) params.getOrDefault("items", List.of())).size();
            return ToolResult.success("计划已创建，共 " + itemCount + " 项");
        } catch (IOException e) {
            return ToolResult.failure("创建计划失败: " + e.getMessage());
        }
    }

    @SuppressWarnings("unchecked")
    private ToolResult cmdUpdate(Path todoFile, Map<String, Object> params) {
        if (!Files.exists(todoFile)) {
            return ToolResult.failure("暂无计划，请先调用 Todo(operation=\"init\", ...) 创建");
        }
        try {
            Map<String, Object> plan = JSON.readValue(todoFile.toFile(), Map.class);
            String itemId = (String) params.get("item_id");
            String newStatus = (String) params.get("status");
            List<Map<String, Object>> items = (List<Map<String, Object>>) plan.get("items");
            boolean found = false;
            for (Map<String, Object> item : items) {
                if (itemId.equals(item.get("id"))) {
                    item.put("status", newStatus);
                    found = true;
                    break;
                }
            }
            if (!found) return ToolResult.failure("未找到项: " + itemId);
            boolean allDone = items.stream().allMatch(i -> "done".equals(i.get("status")));
            if (allDone) plan.put("status", "completed");
            JSON.writeValue(todoFile.toFile(), plan);
            return ToolResult.success("已更新 " + itemId + " → " + newStatus);
        } catch (IOException e) {
            return ToolResult.failure("更新失败: " + e.getMessage());
        }
    }

    private ToolResult cmdList(Path todoFile) {
        if (!Files.exists(todoFile)) {
            return ToolResult.success("暂无计划。需要时调用 Todo(operation=\"init\", ...) 创建。");
        }
        try {
            String content = Files.readString(todoFile);
            return ToolResult.success("当前计划:\n" + content);
        } catch (IOException e) {
            return ToolResult.failure("读取计划失败: " + e.getMessage());
        }
    }
}
