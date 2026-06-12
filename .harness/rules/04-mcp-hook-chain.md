---
description: MCP 工具必须适配为 Tool 接口，走统一 Hook 链。
globs: "**/mcp/**"
---
# MCP 安全统一

## 强制
- 所有 MCP 工具必须通过 `McpToolAdapterImpl` 适配为 `Tool` 接口
- 适配后的工具注册到主 `ToolRegistry`，走完整的 Hook 责任链
- 禁止保留独立的 MCP 工具执行路径

## Hook 链必须包含
1. `SafetyPreHook` — 安全检查
2. `PermissionPreHook` — 权限确认
3. `MetricsHook` — 指标收集
4. `TruncationHook` — 大结果截断

## 装配方式
```java
// 在 Bootstrap 中:
McpClient client = clientFactory.createClient(config);
client.connect(config);
List<McpToolDefinition> mcpTools = client.listTools();
for (McpToolDefinition toolDef : mcpTools) {
    Tool adapted = new McpToolAdapterImpl(toolDef, client);
    toolRegistry.register(adapted);  // 自动获得完整 Hook 链
}
// 移除 McpToolRegistry 的直接执行路径
```

## 追溯
- 源于 #security-review-001: McpToolRegistry.executeTool() 绕过 SafetyPreHook 和 PermissionPreHook
