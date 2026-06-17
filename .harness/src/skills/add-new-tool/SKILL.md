# 新增 Tool 标准流程

## Step 1: 确定归属模块
- 核心工具（文件操作/搜索/执行）→ `src/main/java/com/codemind/tool/impl/`
- 外部集成工具 → `src/main/java/com/codemind/tool/impl/integrate/{provider}/`

## Step 2: 实现 Tool 接口
```java
package com.codemind.tool.impl;

import com.codemind.tool.spi.Tool;
import com.codemind.tool.ToolResult;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.util.Map;

public class MyTool implements Tool {
    private static final ObjectMapper mapper = new ObjectMapper();

    @Override
    public String getName() { return "MyTool"; }

    @Override
    public String getDescription() {
        return "工具描述——第一行作为摘要，LLM 会据此判断何时使用。";
    }

    @Override
    public JsonNode getInputSchema() {
        ObjectNode schema = mapper.createObjectNode();
        schema.put("type", "object");
        ObjectNode props = schema.putObject("properties");
        // ... 定义参数
        schema.putArray("required").add("param1");
        return schema;
    }

    @Override
    public ToolResult execute(Map<String, Object> params) {
        // 实现逻辑
    }
}
```

## Step 3: 注册到 ToolRegistry
在 Bootstrap 或 AssemblyConfig 中添加：
```java
toolRegistry.register(new MyTool());
```

## Step 4: 验证
1. 工具名在 system prompt 中可见
2. LLM 能正确调用
3. 参数校验完整
