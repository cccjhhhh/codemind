# 新增 Compactor 标准流程

## Step 1: 实现 Compactor 接口
```java
package com.codemind.impl.compression;

/**
 * 压缩策略实现。
 * 管线执行顺序: L1 → L2 → L3 → (L4 按需)
 * 新策略根据目的插入对应位置。
 */
public class MyCompactor implements Compactor {

    @Override
    public int order() {
        // L1=10, L2=20, L3=30, L4=40
        // 新策略在中间插入，如 L1.5=15
        return 15;
    }

    @Override
    public List<Message> compact(List<Message> messages, Set<Integer> protectedReadIndices) {
        // 实现压缩逻辑，protectedReadIndices 中的索引不得压缩
    }
}
```

## Step 2: 注册到 Orchestrator
```java
// 在 CompressionModule 装配时
ContextCompressionOrchestrator compressor = new ContextCompressionOrchestrator(
    List.of(
        new L1SnipCompactor(...),
        new MyCompactor(...),     // 新策略
        new L2MicroCompactor(...),
        new L3SpillCompactor(...)
    ),
    new L4SummaryCompactor(llmClient)
);
```

## 约束检查清单
- [ ] 保护 Read 工具结果不被压缩
- [ ] 不破坏 ASSISTANT-TOOL 配对关系
- [ ] order() 值不与已有策略冲突
- [ ] 可逆操作优先于不可逆操作
