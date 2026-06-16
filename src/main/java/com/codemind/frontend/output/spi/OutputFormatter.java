package com.codemind.frontend.output.spi;

import com.codemind.tool.ToolResult;

import java.util.Map;

/**
 * CLI 输出格式化接口
 *
 * 负责将各种输出内容格式化为带 ANSI 样式的字符串。
 * 支持：
 * - 工具调用（开始/结束）
 * - Skill 调用（开始/进度/结束）
 * - 权限请求提示
 * - 思考过程指示器
 * - 进度显示
 *
 * 设计原则（开放封闭原则 OCP）：
 * - 对扩展开放：新增格式化方法无需修改已有代码
 * - 对修改封闭：接口稳定，实现类可自由演进
 * - 事件驱动：新增 Skill/Tool 无需修改此接口或 AgentLoop
 *
 * 设计参考：Claude Code、Cursor、Aider 等主流 Agent CLI
 */
public interface OutputFormatter {

    // ========== 思考指示器（Thinking Indicator）==========

    /**
     * 格式化思考开始
     *
     * 显示 Agent 正在思考/生成响应
     * 典型输出：∴ Thinking...
     */
    default String formatThinkingStart() {
        return "∴ Thinking...\n";
    }

    /**
     * 格式化思考结束
     *
     * 清除思考指示器，准备显示响应
     * 典型输出：\r（回到行首清除）
     */
    default String formatThinkingEnd() {
        return "\r";  // 回到行首清除 thinking 文本
    }

    /**
     * 格式化思考内容（如果需要显示中间思考过程）
     *
     * @param content 思考内容
     */
    default String formatThinkingContent(String content) {
        return "  ∴ " + content + "\n";
    }

    // ========== 进度显示（Progress Display）==========

    /**
     * 格式化迭代进度
     *
     * 显示当前 iteration 和 elapsed time
     * 典型输出：[3/50] 12.3s >
     *
     * @param iteration 当前迭代（从 0 开始）
     * @param total 最大迭代数
     * @param elapsedMs 已用时间（毫秒）
     */
    default String formatProgress(int iteration, int total, long elapsedMs) {
        return "◐ [" + (iteration + 1) + "/" + total + "] " + (elapsedMs / 1000.0) + "s > ";
    }

    /**
     * 格式化 spinner 状态
     *
     * 用于长时间操作的动画反馈
     *
     * @param state spinner 状态（tick/frame index）
     * @return spinner 字符
     */
    default String formatSpinner(int state) {
        String[] frames = {"⠋", "⠙", "⠹", "⠸", "⠼", "⠴", "⠦", "⠧", "⠇", "⠏"};
        return frames[state % frames.length];
    }

    // ========== 工具调用（Tool Calls）==========

    /**
     * 格式化工具调用开始
     *
     * @param toolName 工具名称
     * @param params 工具参数
     * @return 格式化后的字符串
     */
    String formatToolCallStart(String toolName, Map<String, Object> params);

    /**
     * 格式化工具调用结果
     *
     * @param toolName 工具名称
     * @param result 工具执行结果
     * @return 格式化后的字符串
     */
    String formatToolCallEnd(String toolName, ToolResult result);

    // ========== 权限请求（Permissions）==========

    /**
     * 格式化权限请求提示
     *
     * @param toolName 工具名称
     * @param context 权限请求上下文
     * @return 格式化后的字符串
     */
    String formatPermissionPrompt(String toolName, String context);

    // ========== 消息格式化（Messages）==========

    /**
     * 格式化错误信息
     */
    String formatError(String message);

    /**
     * 格式化成功信息
     */
    String formatSuccess(String message);

    /**
     * 格式化警告信息
     */
    String formatWarning(String message);

    /**
     * 格式化思考过程（可选）
     */
    default String formatThinking(String content) {
        return content;
    }

    /**
     * 设置详细输出模式（可选）
     *
     * @param verbose 是否启用详细输出
     */
    default void setVerbose(boolean verbose) {}

    // ========== Skill 格式化方法（新增）==========

    /**
     * 格式化 Skill 调用开始
     *
     * @param skillName Skill 名称
     * @param input 用户输入
     * @return 格式化后的字符串
     */
    default String formatSkillStart(String skillName, String input) {
        return "\n🎯 [Skill 命中] " + skillName + " - 正在执行...\n";
    }

    /**
     * 格式化 Skill 调用进度阶段
     *
     * @param stage 当前阶段名称
     * @return 格式化后的字符串
     */
    default String formatSkillProgress(String stage) {
        return "  ↳ " + stage + "...\n";
    }

    /**
     * 格式化 Skill 调用结束
     *
     * @param skillName Skill 名称
     * @param success 是否成功
     * @param summary 结果摘要
     * @return 格式化后的字符串
     */
    default String formatSkillCallEnd(String skillName, boolean success, String summary) {
        if (success) {
            return " ✓ 完成" + (summary != null ? " → " + summary : "") + "\n";
        } else {
            return " ✗ 失败\n";
        }
    }

    // ========== 工具栏/状态行（Status Line）==========

    /**
     * 格式化状态栏信息
     *
     * 显示模型、成本、session 时长等信息
     *
     * @param model 模型名称
     * @param sessionDurationMs session 时长（毫秒）
     */
    default String formatStatusBar(String model, long sessionDurationMs) {
        return "[" + model + "] " + formatDuration(sessionDurationMs);
    }

    /**
     * 格式化时长
     */
    default String formatDuration(long durationMs) {
        if (durationMs < 1000) {
            return durationMs + "ms";
        } else if (durationMs < 60000) {
            return String.format("%.1fs", durationMs / 1000.0);
        } else {
            return String.format("%.1fm", durationMs / 60000.0);
        }
    }
}
