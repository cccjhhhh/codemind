package com.codemind.impl.skill;

import com.codemind.api.session.SessionContext;
import com.codemind.api.skill.Skill;
import com.codemind.api.skill.SkillContext;
import com.codemind.api.skill.SkillResult;
import com.codemind.api.tool.Tool;
import com.codemind.api.tool.ToolResult;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;

import java.util.Map;

/**
 * Skill 包装成 Tool 的适配器
 * 
 * 作用：将 Skill 转换为 LLM 可调用的 Tool
 * 设计：一个 SkillAsTool 类通用包装所有 Skill，无需为每个 Skill 写新包装器
 * 
 * 使用方式：
 * <pre>
 * Skill codeReviewSkill = new CodeReviewSkill();
 * toolRegistry.register(new SkillAsTool(codeReviewSkill, sessionContext, toolRegistry));
 * </pre>
 * 
 * 学习要点：
 * - 适配器模式：Tool 接口 → Skill 接口
 * - 委托：实际执行委托给 Skill
 * - 参数转换：Map → SkillContext
 */
public class SkillAsTool implements Tool {
    
    private static final ObjectMapper JSON = new ObjectMapper();
    
    private final Skill skill;
    private final SessionContext sessionContext;
    private final com.codemind.api.tool.ToolRegistry toolRegistry;
    
    public SkillAsTool(Skill skill, SessionContext sessionContext, com.codemind.api.tool.ToolRegistry toolRegistry) {
        this.skill = skill;
        this.sessionContext = sessionContext;
        this.toolRegistry = toolRegistry;
    }
    
    @Override
    public String getName() {
        return skill.getName();
    }
    
    @Override
    public String getDescription() {
        return skill.getDescription();
    }
    
    @Override
    public JsonNode getInputSchema() {
        // Skill 共用的参数 schema
        ObjectNode schema = JSON.createObjectNode();
        schema.put("type", "object");
        
        ObjectNode properties = schema.putObject("properties");
        
        ObjectNode userInputProp = properties.putObject("user_input");
        userInputProp.put("type", "string");
        userInputProp.put("description", "用户输入或指令");
        
        ObjectNode skillParamProp = properties.putObject("skill_param");
        skillParamProp.put("type", "string");
        skillParamProp.put("description", "Skill 特定参数（可选）");
        
        schema.set("required", JSON.createArrayNode());
        
        return schema;
    }
    
    @Override
    public ToolResult execute(Map<String, Object> params) {
        try {
            // 从 params 中提取信息构建 SkillContext
            String userInput = extractString(params, "user_input", "");
            
            // 构建 SkillContext，传入 toolRegistry 让 Skill 可以调用其他 Tool
            SkillContext context = new SkillContext(
                sessionContext,
                skill.getName(),
                userInput,
                toolRegistry
            );
            
            // 调用 Skill
            SkillResult result = skill.execute(context);
            
            // 转换 SkillResult → ToolResult
            if (result.isSuccess()) {
                return ToolResult.success(result.getOutput());
            } else {
                return ToolResult.failure(result.getError());
            }
            
        } catch (Exception e) {
            return ToolResult.failure("SkillAsTool execution failed: " + e.getMessage());
        }
    }
    
    private String extractString(Map<String, Object> params, String key, String defaultValue) {
        Object value = params.get(key);
        if (value instanceof String) {
            return (String) value;
        }
        return defaultValue;
    }
}
