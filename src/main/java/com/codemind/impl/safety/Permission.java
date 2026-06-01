package com.codemind.impl.safety;

import java.util.Set;

/**
 * 权限枚举
 * 
 * 定义 Agent 可执行操作的权限级别
 */
public enum Permission {
    /** 读取文件 */
    READ_FILE("读取文件系统中的文件"),
    
    /** 写入文件 */
    WRITE_FILE("写入或修改文件系统中的文件"),
    
    /** 执行命令 */
    EXECUTE_COMMAND("执行 shell 命令"),
    
    /** 网络访问 */
    NETWORK_ACCESS("访问网络资源"),
    
    /** 安装依赖 */
    INSTALL_PACKAGE("安装软件包或依赖");
    
    private final String description;
    
    Permission(String description) {
        this.description = description;
    }
    
    public String getDescription() {
        return description;
    }
}