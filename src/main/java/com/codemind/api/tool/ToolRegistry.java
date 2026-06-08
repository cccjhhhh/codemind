package com.codemind.api.tool;

import com.codemind.api.llm.ToolDefinition;
import com.codemind.impl.skill.SkillDefinition;
import java.util.List;
import java.util.Map;

/**
 * 工具注册中心接口
 */
public interface ToolRegistry {
    
    /**
     * 注册工具
     */
    void register(Tool tool);
    
    /**
     * 注销工具
     */
    void unregister(String name);
    
    /**
     * 获取工具
     */
    Tool get(String name);
    
    /**
     * 执行工具（带权限检查）
     */
    ToolResult execute(String name, Map<String, Object> params);
    
    /**
     * 获取工具定义（用于 LLM Function Calling）
     */
    ToolDefinition getDefinition(String name);
    
    /**
     * 获取所有工具定义
     */
    List<ToolDefinition> getAllDefinitions();

    /**
     * 注册工具执行 Hook，追加到链尾
     */
    void registerHook(ToolHook hook);

    /**
     * 通过类名移除 Hook
     */
    void removeHook(String hookName);
    
    /**
     * 根据 skill 的 allowedTools 过滤工具定义。
     *
     * <p>行为契约：
     * <ul>
     *   <li>skill 为 null → 返回所有工具定义（向后兼容）</li>
     *   <li>skill.allowedTools 为空 → 返回所有工具定义（向后兼容）</li>
     *   <li>skill.allowedTools 非空 → 仅返回名称在列表中的工具；列表中不存在的工具名被安静忽略</li>
     * </ul>
     *
     * @param skill 当前激活的 skill，可能为 null
     * @return 过滤后的工具定义列表
     */
    List<ToolDefinition> getDefinitionsForSkill(SkillDefinition skill);
    
    /**
     * 检查工具是否存在
     */
    boolean hasTool(String name);
}