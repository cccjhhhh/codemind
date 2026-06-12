package com.codemind.common.exception;

/**
 * 配置异常。设置文件加载或解析失败时抛出。
 */
public class ConfigurationException extends AgentException {

    public ConfigurationException(String message) {
        super(message);
    }

    public ConfigurationException(String message, Throwable cause) {
        super(message, cause);
    }
}
