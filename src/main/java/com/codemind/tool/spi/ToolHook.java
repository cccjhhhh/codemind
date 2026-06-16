package com.codemind.tool.spi;

import com.codemind.tool.ToolResult;
import java.util.Map;

public interface ToolHook {
    default void preExecute(String toolName, Map<String, Object> args) {}
    default void postExecute(String toolName, ToolResult result, long elapsedMs) {}
}
