package com.codemind.impl.hook;

import com.codemind.api.safety.PermissionGate;
import com.codemind.api.safety.PermissionLevel;
import com.codemind.api.tool.ToolHook;
import com.codemind.api.tool.ToolResult;
import java.util.Map;

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
