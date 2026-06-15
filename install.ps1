# install.ps1 - CodeMind Windows 安装脚本
# 自动下载 codemind.jar 并配置 PATH

$ErrorActionPreference = "Stop"

$INSTALL_DIR = "$env:USERPROFILE\.codemind\bin"
$JAR_NAME = "codemind.jar"
$REPO_URL = "https://github.com/anthropics/codemind"

Write-Host ""
Write-Host "╔════════════════════════════════════════╗" -ForegroundColor Cyan
Write-Host "║         CodeMind Installer             ║" -ForegroundColor Cyan
Write-Host "╚════════════════════════════════════════╝" -ForegroundColor Cyan
Write-Host ""

# 检查 Java 版本
Write-Host "Checking Java version..." -ForegroundColor Yellow
try {
    $javaVersion = java -version 2>&1 | Select-String -Pattern 'version "(\d+)' | ForEach-Object { $_.Matches[0].Groups[1].Value }
    if ([int]$javaVersion -lt 17) {
        Write-Host "Error: Java 17 or higher is required. Found: Java $javaVersion" -ForegroundColor Red
        Write-Host "Download from: https://adoptium.net/" -ForegroundColor Yellow
        exit 1
    }
    Write-Host "Java $javaVersion detected" -ForegroundColor Green
} catch {
    Write-Host "Error: Java not found. Please install Java 17+" -ForegroundColor Red
    Write-Host "Download from: https://adoptium.net/" -ForegroundColor Yellow
    exit 1
}

# 创建安装目录
Write-Host "Creating install directory..." -ForegroundColor Yellow
if (!(Test-Path $INSTALL_DIR)) {
    New-Item -ItemType Directory -Path $INSTALL_DIR -Force | Out-Null
}

# 获取最新版本号
Write-Host "Fetching latest version..." -ForegroundColor Yellow
try {
    $latestRelease = Invoke-RestMethod -Uri "https://api.github.com/repos/anthropics/codemind/releases/latest" -UseBasicParsing
    $downloadUrl = $latestRelease.assets | Where-Object { $_.name -eq "codemind.jar" } | Select-Object -ExpandProperty browser_download_url
    if (-not $downloadUrl) {
        Write-Host "Error: Could not find codemind.jar in latest release" -ForegroundColor Red
        exit 1
    }
} catch {
    Write-Host "Warning: Could not fetch latest version, using direct URL" -ForegroundColor Yellow
    $downloadUrl = "$REPO_URL/releases/latest/download/codemind.jar"
}

# 下载 JAR
Write-Host "Downloading CodeMind..." -ForegroundColor Yellow
try {
    Invoke-WebRequest -Uri $downloadUrl -OutFile "$INSTALL_DIR\$JAR_NAME" -UseBasicParsing
    Write-Host "Download complete!" -ForegroundColor Green
} catch {
    Write-Host "Error: Download failed - $($_.Exception.Message)" -ForegroundColor Red
    exit 1
}

# 创建 codemind 启动脚本
Write-Host "Creating launcher script..." -ForegroundColor Yellow
$launcherContent = @"
@echo off
java -jar "%~dp0codemind.jar" %*
"@
Set-Content -Path "$INSTALL_DIR\codemind.cmd" -Value $launcherContent

# 添加到 PATH（如果还没加）
$currentPath = [Environment]::GetEnvironmentVariable("Path", "User")
if ($currentPath -notlike "*$INSTALL_DIR*") {
    [Environment]::SetEnvironmentVariable("Path", "$currentPath;$INSTALL_DIR", "User")
    Write-Host "Added to PATH" -ForegroundColor Green
    Write-Host "Please restart your terminal for PATH changes to take effect." -ForegroundColor Yellow
} else {
    Write-Host "Already in PATH" -ForegroundColor Green
}

Write-Host ""
Write-Host "Installation complete!" -ForegroundColor Green
Write-Host ""
Write-Host "Usage:" -ForegroundColor Cyan
Write-Host "  1. Open a new terminal" -ForegroundColor White
Write-Host "  2. cd /path/to/your/project" -ForegroundColor White
Write-Host "  3. codemind" -ForegroundColor White
Write-Host ""
Write-Host "First run: Edit ~/.codemind/settings.json to add your API key" -ForegroundColor Yellow
Write-Host ""
