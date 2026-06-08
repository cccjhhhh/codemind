package com.codemind.api.tool;

import java.util.Map;

public interface ToolHook {
    default void preExecute(String toolName, Map<String, Object> args) {}
    default void postExecute(String toolName, ToolResult result, long elapsedMs) {}
}
