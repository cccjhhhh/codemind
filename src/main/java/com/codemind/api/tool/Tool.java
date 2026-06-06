package com.codemind.api.tool;

import com.codemind.api.safety.PermissionLevel;
import com.fasterxml.jackson.databind.JsonNode;

import java.util.Map;
import java.util.Optional;

/**
 * 工具接口
 * 
 * 所有 Agent 工具必须实现此接口。
 * 学习要点：工具的标准化定义、参数验证、执行与结果处理
 * 
 * 设计原则：
 * - 单一职责：每个工具只做一件事
 * - 接口隔离：方法少而专注
 * - 依赖倒置：高层模块依赖此接口
 */
public interface Tool {
    
    /**
     * 工具名称（唯一标识）
     * 使用 PascalCase 命名（如 Read, Write, Grep）
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
    
    /**
     * 获取已废弃的工具名称（向后兼容）
     * 
     * 如果工具在重构后更改了名称，旧名称可以通过此方法暴露。
     * LLM 会自动处理别名映射。
     * 
     * @return 废弃的工具名称，如果无废弃名称则返回空
     */
    default Optional<String> getDeprecatedName() {
        return Optional.empty();
    }
    
    /**
     * 获取工具的默认权限级别
     * 
     * 此级别用于权限检查的默认决策。
     * 可以通过 PermissionGate 运行时覆盖。
     * 
     * @return 默认权限级别
     */
    PermissionLevel getDefaultPermission();
}