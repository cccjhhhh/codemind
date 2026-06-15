#!/bin/bash
# install.sh - CodeMind Linux/Mac 安装脚本
# 自动下载 codemind.jar 并配置 PATH

set -e

INSTALL_DIR="$HOME/.codemind/bin"
JAR_NAME="codemind.jar"
REPO_URL="https://github.com/anthropics/codemind"

# 颜色定义
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
CYAN='\033[0;36m'
NC='\033[0m' # No Color

echo ""
echo -e "${CYAN}╔════════════════════════════════════════╗${NC}"
echo -e "${CYAN}║         CodeMind Installer             ║${NC}"
echo -e "${CYAN}╚════════════════════════════════════════╝${NC}"
echo ""

# 检查 Java 版本
echo -e "${YELLOW}Checking Java version...${NC}"
if ! command -v java &> /dev/null; then
    echo -e "${RED}Error: Java not found. Please install Java 17+${NC}"
    echo -e "${YELLOW}Install with: brew install openjdk@17 (macOS) or sudo apt install openjdk-17-jdk (Ubuntu)${NC}"
    exit 1
fi

JAVA_VERSION=$(java -version 2>&1 | head -1 | cut -d'"' -f2 | cut -d'.' -f1)
if [ "$JAVA_VERSION" -lt 17 ]; then
    echo -e "${RED}Error: Java 17 or higher is required. Found: Java $JAVA_VERSION${NC}"
    exit 1
fi
echo -e "${GREEN}Java $JAVA_VERSION detected${NC}"

# 创建安装目录
echo -e "${YELLOW}Creating install directory...${NC}"
mkdir -p "$INSTALL_DIR"

# 获取最新版本号
echo -e "${YELLOW}Fetching latest version...${NC}"
LATEST_URL=$(curl -fsSL "https://api.github.com/repos/anthropics/codemind/releases/latest" 2>/dev/null | grep -o '"browser_download_url": "[^"]*codemind.jar"' | cut -d'"' -f4)

if [ -z "$LATEST_URL" ]; then
    echo -e "${YELLOW}Warning: Could not fetch latest version, using direct URL${NC}"
    LATEST_URL="$REPO_URL/releases/latest/download/codemind.jar"
fi

# 下载 JAR
echo -e "${YELLOW}Downloading CodeMind...${NC}"
if ! curl -fsSL "$LATEST_URL" -o "$INSTALL_DIR/$JAR_NAME"; then
    echo -e "${RED}Error: Download failed${NC}"
    exit 1
fi
echo -e "${GREEN}Download complete!${NC}"

# 创建 codemind 启动脚本
echo -e "${YELLOW}Creating launcher script...${NC}"
cat > "$INSTALL_DIR/codemind" << 'EOF'
#!/bin/bash
exec java -jar "$(dirname "$0")/codemind.jar" "$@"
EOF
chmod +x "$INSTALL_DIR/codemind"

# 添加到 PATH
SHELL_RC="$HOME/.bashrc"
if [[ "$SHELL" == */zsh ]]; then
    SHELL_RC="$HOME/.zshrc"
elif [[ "$SHELL" == */fish ]]; then
    SHELL_RC="$HOME/.config/fish/config.fish"
fi

if ! grep -q "$INSTALL_DIR" "$SHELL_RC" 2>/dev/null; then
    if [[ "$SHELL" == */fish ]]; then
        echo "set -gx PATH \$PATH $INSTALL_DIR" >> "$SHELL_RC"
    else
        echo "export PATH=\"\$PATH:$INSTALL_DIR\"" >> "$SHELL_RC"
    fi
    echo -e "${GREEN}Added to PATH${NC}"
    echo -e "${YELLOW}Please restart your terminal for PATH changes to take effect.${NC}"
else
    echo -e "${GREEN}Already in PATH${NC}"
fi

echo ""
echo -e "${GREEN}Installation complete!${NC}"
echo ""
echo -e "${CYAN}Usage:${NC}"
echo "  1. Open a new terminal"
echo "  2. cd /path/to/your/project"
echo "  3. codemind"
echo ""
echo -e "${YELLOW}First run: Edit ~/.codemind/settings.json to add your API key${NC}"
echo ""
