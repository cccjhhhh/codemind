---
description: 测试代码本地化管理规则。
globs: ".gitignore"
---
# 测试代码管理

## 强制
- 所有 `src/test/` 目录必须加入 `.gitignore`
- 测试依赖（JUnit、Mockito）保留在 POM 的 `<scope>test</scope>` 中

## 禁止
- ❌ 禁止将测试文件提交到 git 版本管理
- ❌ 禁止使用 `git add -A` 批量添加文件（可能误加测试文件）

## 本地开发
- `mvn test` 在本地正常使用，`src/test/` 目录存在于文件系统
- 测试基类和 Mock 工厂放在 `codemind-test/` 模块中
- `.harness/changes/` 目录也归属本地，不入库

## 从已有仓库清理
```bash
# 从 git 移除已跟踪的测试文件
git rm --cached src/test/java/com/codemind/core/RecoveryManagerTest.java
# ... 其他测试文件
```

## 追溯
- 源于 #arch-review-007: 当前 13 个测试文件在 git 中，与用户要求冲突
