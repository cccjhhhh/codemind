package com.codemind.api.safety;

/**
 * Agent 权限枚举
 * 定义 Agent 可以执行的操作类型
 */
public enum Permission {
    READ_FILE("读取文件系统中的文件"),
    WRITE_FILE("写入或修改文件系统中的文件"),
    EXECUTE_COMMAND("执行 shell 命令"),
    NETWORK_ACCESS("访问网络资源"),
    INSTALL_PACKAGE("安装软件包或依赖");
    
    private final String description;
    
    Permission(String description) {
        this.description = description;
    }
    
    public String getDescription() {
        return description;
    }
}
