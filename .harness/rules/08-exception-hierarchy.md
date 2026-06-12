---
description: 完整的 AgentException 继承体系，禁止抛出散落的 RuntimeException。
globs: "**/common/exception/**"
---
# 异常分层规范

## 异常继承体系
```
AgentException (RuntimeException)    ← 所有异常的基类
├── ContextOverflowException         ← 上下文超限
├── LLMException                     ← LLM 调用异常
│   ├── LLMTimeoutException          ← LLM 超时
│   ├── LLMAuthException             ← API Key 错误
│   ├── LLMRateLimitException        ← 429 限流
│   └── LLMContextLengthException    ← prompt_too_long
├── ToolExecutionException           ← 工具执行异常
│   ├── ToolPermissionException      ← 权限拒绝
│   └── ToolNotFoundException        ← 工具未注册
├── SessionException                 ← 会话异常
│   └── SessionPersistenceException  ← 持久化失败
└── ConfigurationException           ← 配置异常
```

## 强制
- 所有业务异常必须继承自 `AgentException`
- `catch (Exception e)` 后不得直接 `throw new RuntimeException(e)`
- 异常必须包含业务可读的 message（中文/英文均可）

## 追溯
- 源于 #arch-review-008: 当前项目中 ContextLengthException 是独立异常，其他错误都是 RuntimeException
