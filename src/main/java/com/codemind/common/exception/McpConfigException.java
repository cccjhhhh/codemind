package com.codemind.common.exception;

/**
 * MCP 配置异常
 *
 * 当配置文件格式错误或加载失败时抛出。
 */
public class McpConfigException extends AgentException {

    private final String configPath;

    public McpConfigException(String configPath, String message) {
        super(message);
        this.configPath = configPath;
    }

    public McpConfigException(String configPath, String message, Throwable cause) {
        super(message, cause);
        this.configPath = configPath;
    }

    public String getConfigPath() {
        return configPath;
    }
}
