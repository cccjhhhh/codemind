# CodeMind

智能编程助手 - AI Agent for Coding

## 安装

### Windows
```powershell
irm https://raw.githubusercontent.com/anthropics/codemind/main/install.ps1 | iex
```

### Linux / Mac
```bash
curl -fsSL https://raw.githubusercontent.com/anthropics/codemind/main/install.sh | bash
```

### 手动安装
1. 从 [GitHub Releases](https://github.com/anthropics/codemind/releases) 下载 `codemind.jar`
2. 放到任意目录
3. 运行 `java -jar codemind.jar`

## 使用

```bash
cd /path/to/your/project
codemind
```

### 指定项目目录
```bash
codemind -p /path/to/other/project
```

### 首次运行
1. 编辑 `~/.codemind/settings.json`
2. 将 `YOUR_*_API_KEY` 替换为真实的 API Key
3. 支持 DeepSeek、GPT-4o 等模型

## 命令

| 命令 | 说明 |
|------|------|
| `/models` | 列出所有可用模型 |
| `/switch` | 切换模型 |
| `/sessions` | 列出已保存的会话 |
| `/load <id>` | 加载指定会话 |
| `/allow <工具>` | 授权危险操作 |
| `/permissions` | 显示权限状态 |
| `/help` | 显示帮助 |
| `quit` | 退出 |

## 配置

配置文件按优先级加载：
1. `~/.codemind/settings.json`（全局默认）
2. `<项目>/.codemind/settings.json`（项目级）
3. `<项目>/.codemind/settings.local.json`（本地覆盖）

详见 [安装文档](docs/installation.md)。
