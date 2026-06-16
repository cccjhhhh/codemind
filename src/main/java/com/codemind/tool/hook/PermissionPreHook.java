package com.codemind.tool.hook;

import com.codemind.safety.PermissionGate;
import com.codemind.safety.PermissionLevel;
import com.codemind.tool.spi.ToolHook;
import com.codemind.tool.ToolResult;

import java.util.Map;

/**
 * 权限前置钩子 — 工具执行前的权限确认。
 * 根据工具的默认权限等级（DENY/ASK/ALLOW）决定是否放行：
 * DENY 直接拒绝，ASK 弹窗询问用户，ALLOW 自动通过。
 * 属于第二层防线，接在 SafetyPreHook 之后。
 */
public class PermissionPreHook implements ToolHook {

    private final PermissionGate permissionGate;

    public PermissionPreHook(PermissionGate permissionGate) {
        this.permissionGate = permissionGate;
    }

    @Override
    public void preExecute(String toolName, Map<String, Object> args) {
        PermissionLevel level = permissionGate.getDefaultLevel(toolName);

        if (level == PermissionLevel.DENY) {
            throw new SecurityException("Tool " + toolName + " 被默认拒绝");
        }

        if (level == PermissionLevel.ASK) {
            boolean granted = permissionGate.requestPermission(toolName,
                "工具 " + toolName + " 请求执行: " + args);
            if (!granted) {
                throw new SecurityException("用户拒绝授权: " + toolName);
            }
        }
    }

    @Override
    public void postExecute(String toolName, ToolResult result, long elapsedMs) {}
}
