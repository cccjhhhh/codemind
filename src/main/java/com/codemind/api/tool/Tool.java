package com.codemind.api.tool;

import com.fasterxml.jackson.databind.JsonNode;
import java.util.Map;

/**
 * 工具接口
 * 
 * 所有 Agent 工具必须实现此接口。
 * 学习要点：工具的标准化定义、参数验证、执行与结果处理
 */
public interface Tool {
    
    /**
     * 工具名称（唯一标识）
     */
    String getName();
    
    /**
     * 工具描述（LLM 用于理解工具用途）
     */
    String getDescription();
    
    /**
     * 输入参数的 JSON Schema
     */
    JsonNode getInputSchema();
    
    /**
     * 执行工具
     * 
     * @param params 输入参数
     * @return 执行结果
     */
    ToolResult execute(Map<String, Object> params);
}