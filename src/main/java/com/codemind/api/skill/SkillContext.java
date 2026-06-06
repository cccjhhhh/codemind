package com.codemind.api.skill;

import com.codemind.api.safety.PermissionGate;
import com.codemind.api.safety.PermissionLevel;
import com.codemind.api.session.SessionContext;
import com.codemind.api.tool.ToolRegistry;
import com.codemind.api.tool.ToolResult;

import java.util.Map;
import java.util.Set;

/**
 * 技能执行上下文
 *
 * 包含技能执行所需的所有信息，包括调用其他 Tool 的能力
 *
 * 学习要点：
 * - Skill 如何通过 context 调用其他 Tool
 * - 依赖注入：ToolRegistry 通过构造器注入
 */
public class SkillContext {

    private final SessionContext sessionContext;
    private final String skillName;
    private final String userInput;
    private final ToolRegistry toolRegistry;
    private final PermissionGate permissionGate;

    // 内置 Skill 所需的工具集合（临时授予 ALLOW 级别）
    private static final Set<String> BUILTIN_SKILL_TOOLS = Set.of(
        "Read", "Bash"
    );

    public SkillContext(SessionContext sessionContext, String skillName, String userInput) {
        this(sessionContext, skillName, userInput, null, null);
    }

    public SkillContext(SessionContext sessionContext, String skillName, String userInput,
                        ToolRegistry toolRegistry, PermissionGate permissionGate) {
        this.sessionContext = sessionContext;
        this.skillName = skillName;
        this.userInput = userInput;
        this.toolRegistry = toolRegistry;
        this.permissionGate = permissionGate;

        // 如果是内置 Skill，自动授予权限
        if (isBuiltinSkill()) {
            grantBuiltinPermissions();
        }
    }

    /**
     * 检查是否是内置 Skill
     */
    private boolean isBuiltinSkill() {
        if (skillName == null) return false;
        return skillName.equals("code_review") ||
               skillName.equals("analyze_logs");
    }

    /**
     * 授予内置 Skill 所需的权限
     */
    private void grantBuiltinPermissions() {
        if (permissionGate != null) {
            for (String toolName : BUILTIN_SKILL_TOOLS) {
                permissionGate.setDefaultLevel(toolName, PermissionLevel.ALLOW);
            }
        }
    }

    /**
     * 撤销内置 Skill 的权限
     */
    public void revokeBuiltinPermissions() {
        if (permissionGate != null && isBuiltinSkill()) {
            for (String toolName : BUILTIN_SKILL_TOOLS) {
                permissionGate.setDefaultLevel(toolName, PermissionLevel.ASK);
            }
        }
    }

    /**
     * 调用其他 Tool
     *
     * Skill 可以通过此方法调用注册在 ToolRegistry 中的其他 Tool，
     * 实现复杂任务的编排
     *
     * @param toolName 工具名称
     * @param params 工具参数
     * @return 工具执行结果
     */
    public ToolResult callTool(String toolName, Map<String, Object> params) {
        if (toolRegistry == null) {
            return ToolResult.failure("ToolRegistry not available in SkillContext");
        }
        return toolRegistry.execute(toolName, params);
    }

    /**
     * 检查是否有 ToolRegistry
     */
    public boolean hasToolRegistry() {
        return toolRegistry != null;
    }

    // Getters
    public SessionContext getSessionContext() { return sessionContext; }
    public String getSkillName() { return skillName; }
    public String getUserInput() { return userInput; }
}
