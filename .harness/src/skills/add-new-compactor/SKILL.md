# 新增 Compactor 标准流程

## Step 1: 实现 Compactor 接口
```java
package com.codemind.context;

import com.codemind.llm.Message;
import java.util.List;
import java.util.Set;

/**
 * 压缩策略实现。
 * 管线执行顺序: L1 → L2 → L3 → (L4 按需)
 */
public class MyCompactor implements Compactor {

    @Override
    public int order() {
        // L1=10, L2=20, L3=30, L4=40
        // 新策略在中间插入，如 L1.5=15
        return 15;
    }

    @Override
    public String name() {
        return "MyCompactor";
    }

    @Override
    public List<Message> compact(List<Message> messages, Set<Integer> protectedReadIndices) {
        // 实现压缩逻辑
    }
}
```

## Step 2: 注册到 Orchestrator
```java
ContextCompressionOrchestrator compressor = new ContextCompressionOrchestrator(
    List.of(
        new L1SnipCompactor(...),
        new MyCompactor(...),
        new L2MicroCompactor(...),
        new L3SpillCompactor(...)
    )
);
```

## 约束检查清单
- [ ] 保护 Read 工具结果不被压缩（使用 protectedReadIndices）
- [ ] 不破坏 ASSISTANT-TOOL 配对关系
- [ ] order() 值不与已有策略冲突（L1=10, L2=20, L3=30, L4=40）
