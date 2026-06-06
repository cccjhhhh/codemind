package com.codemind.api.safety;

/**
 * 权限网关接口
 *
 * 控制工具执行的权限。
 * 学习要点：权限控制模型、白名单机制、用户确认流程
 *
 * 设计原则：依赖倒置原则（DIP）
 * - 高层模块依赖此接口，而非具体实现
 * - 实现类可以有不同的策略（CLI确认、配置文件、自动允许等）
 *
 * 权限模型（三层）：
 * - ALLOW: 自动允许，无需确认
 * - ASK: 需要用户确认
 * - DENY: 禁止执行
 *
 * @see PermissionLevel
 */
public interface PermissionGate {

    /**
     * 获取工具的默认权限级别
     *
     * @param toolName 工具名称
     * @return 权限级别
     */
    PermissionLevel getDefaultLevel(String toolName);

    /**
     * 设置工具的默认权限级别（运行时覆盖）
     *
     * @param toolName 工具名称
     * @param level 权限级别
     */
    void setDefaultLevel(String toolName, PermissionLevel level);

    /**
     * 请求用户授权
     *
     * 对于需要确认的操作，通过用户交互获取授权决策。
     *
     * @param toolName 工具名称
     * @param context 操作上下文（如工具名、参数摘要）
     * @return true 如果用户授权，false 如果拒绝
     */
    boolean requestPermission(String toolName, String context);
}
