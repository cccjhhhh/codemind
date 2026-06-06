package com.codemind.impl.tool;

import com.codemind.api.safety.PermissionLevel;
import com.codemind.api.tool.Tool;
import com.codemind.api.tool.ToolResult;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;

import java.util.Map;
import java.util.Optional;

/**
 * Agent 工具
 * 
 * 用于调用子 Agent 执行复杂任务。
 * 
 * 学习要点：
 * - 子 Agent 调用设计
 * - 任务委派模式
 * - 权限隔离
 * 
 * 参考设计：Claude Code Agent Tool
 */
public class AgentTool implements Tool {
    
    private static final ObjectMapper JSON = new ObjectMapper();
    
    @Override
    public String getName() {
        return "Agent";
    }
    
    @Override
    public String getDescription() {
        return "调用子 Agent 执行复杂任务。用于多步任务、深度搜索、批量操作等场景。";
    }
    
    @Override
    public PermissionLevel getDefaultPermission() {
        return PermissionLevel.DENY;  // 默认禁止，强制确认
    }
    
    @Override
    public Optional<String> getDeprecatedName() {
        return Optional.empty();  // 无废弃名称
    }
    
    @Override
    public JsonNode getInputSchema() {
        ObjectNode schema = JSON.createObjectNode();
        schema.put("type", "object");
        
        ObjectNode properties = schema.putObject("properties");
        
        ObjectNode taskProp = properties.putObject("task");
        taskProp.put("type", "string");
        taskProp.put("description", "要执行的任务描述");
        
        ObjectNode typeProp = properties.putObject("type");
        typeProp.put("type", "string");
        typeProp.put("description", "Agent 类型：explore, librarian, oracle");
        
        schema.putArray("required").add("task");
        
        return schema;
    }
    
    @Override
    public ToolResult execute(Map<String, Object> params) {
        // TODO: 实现 Agent 调用
        // 当前返回提示信息，后续集成 AgentLoop
        
        String task = (String) params.get("task");
        String type = (String) params.get("type");
        
        if (task == null || task.isEmpty()) {
            return ToolResult.failure("参数 'task' 是必需的");
        }
        
        // 返回占位信息
        String agentType = type != null ? type : "general";
        
        return ToolResult.success(
            "[Agent 工具尚未实现]\n" +
            "任务: " + task + "\n" +
            "类型: " + agentType + "\n" +
            "注意: 此功能需要集成 AgentLoop，请等待后续版本。"
        );
    }
}