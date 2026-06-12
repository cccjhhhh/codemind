---
description: 模块依赖方向约束。下层绝不能依赖上层，api 和 domain 零外部依赖。
globs: "pom.xml"
---
# 模块依赖方向

## 强制
- `codemind-domain` → 零依赖（纯 POJO，不含任何框架注解）
- `codemind-api` → 仅依赖 domain（纯接口/SPI）
- `codemind-core` → 仅依赖 api + domain（纯编排，不含具体实现）
- `codemind-impl` → 依赖 core + api + domain（具体实现）
- `codemind-mcp` → 依赖 api + domain（MCP 协议实现）
- `codemind-bootstrap` → 依赖所有模块（仅启动装配，不含业务逻辑）

## 禁止
- ❌ impl → bootstrap（实现不能依赖启动器）
- ❌ core → impl（核心不能依赖实现）
- ❌ api → core（接口不能依赖核心）
- ❌ 任何循环依赖

## 检查方式
```bash
# 在每个模块的 pom.xml 中检查依赖，确保没有反向依赖
mvn dependency:analyze
```

## 追溯
- 源于 #arch-review: 当前单模块无依赖约束，各包相互引用随意
