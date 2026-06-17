# CodeMind 安装指南

## 系统要求

- **Java**: 17 或更高版本
- **操作系统**: Windows 10+, macOS 10.15+, Ubuntu 18.04+
- **磁盘空间**: 约 100MB（JAR + 配置）

## 安装方法

### 方法 1: 一键安装脚本（推荐）

#### Windows

```powershell
irm https://raw.githubusercontent.com/anthropics/codemind/main/install.ps1 | iex
```

#### Linux / Mac

```bash
curl -fsSL https://raw.githubusercontent.com/anthropics/codemind/main/install.sh | bash
```

### 方法 2: 手动安装

1. 从 [GitHub Releases](https://github.com/anthropics/codemind/releases) 下载最新版本的 `codemind.jar`
2. 将 JAR 文件放到你喜欢的目录
3. 确保该目录在系统的 PATH 环境变量中

### 方法 3: 从源码构建

```bash
git clone https://github.com/anthropics/codemind.git
cd codemind
mvn clean package -DskipTests
java -jar target/codemind-1.0.0-SNAPSHOT.jar
```

## 首次配置

### 1. 配置 API Key

编辑 `~/.codemind/settings.json`，将 `YOUR_*_API_KEY` 替换为真实的 API Key：

```json
{
    "models": {
        "deepseek": {
            "name": "DeepSeek",
            "type": "openai_compatible",
            "baseUrl": "https://api.deepseek.com/v1",
            "defaultModel": "deepseek-chat",
            "apiKey": "sk-your-actual-api-key"
        }
    },
    "currentModel": "deepseek"
}
```

### 2. 验证安装

```bash
cd /path/to/any/project
codemind --version
```

应该显示版本号，如 `1.0.0`。

## 使用方法

### 基本用法

```bash
cd /path/to/your/project
codemind
```

### 指定项目目录

```bash
codemind -p /path/to/other/project
```

### 查看帮助

```bash
codemind --help
```

## 常见问题

### Q: 提示 "Java not found"

**A:** 需要安装 Java 17 或更高版本。

- **Windows**: 下载 [Eclipse Temurin](https://adoptium.net/)
- **macOS**: `brew install openjdk@17`
- **Ubuntu**: `sudo apt install openjdk-17-jdk`

### Q: 提示 "Invalid API Key"

**A:** 检查 `~/.codemind/settings.json` 中的 `apiKey` 字段是否正确。

### Q: 命令找不到 (command not found)

**A:** 确保安装目录在 PATH 中：

- **Windows**: 检查系统环境变量
- **Linux/Mac**: 运行 `source ~/.bashrc` 或重新打开终端

### Q: 如何卸载？

**A:** 删除以下文件和目录：

```bash
# 删除安装目录
rm -rf ~/.codemind/bin

# 从 PATH 中移除（编辑 ~/.bashrc 或 ~/.zshrc）
```

## 相关文档

- [README.md](README.md) - 项目概述
