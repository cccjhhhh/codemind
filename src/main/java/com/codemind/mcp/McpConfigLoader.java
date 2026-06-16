package com.codemind.mcp;

import com.codemind.mcp.McpServerConfig;
import com.codemind.common.exception.McpConfigException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.File;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;

/**
 * MCP 配置加载器
 *
 * 从配置文件加载 MCP 服务器配置。
 */
public class McpConfigLoader {

    private final ObjectMapper objectMapper;

    public McpConfigLoader() {
        this.objectMapper = new ObjectMapper();
    }

    /**
     * 加载 MCP 服务器配置
     *
     * @param configFile 配置文件路径
     * @return 服务器配置映射
     */
    public Map<String, McpServerConfig> load(Path configFile) {
        Map<String, McpServerConfig> servers = new HashMap<>();

        File file = configFile.toFile();
        if (!file.exists()) {
            return servers;
        }

        try {
            JsonNode root = objectMapper.readTree(file);
            JsonNode mcpServers = root.get("mcpServers");

            if (mcpServers != null && mcpServers.isObject()) {
                mcpServers.fields().forEachRemaining(entry -> {
                    String serverName = entry.getKey();
                    JsonNode serverNode = entry.getValue();

                    McpServerConfig config = objectMapper.convertValue(serverNode, McpServerConfig.class);
                    servers.put(serverName, config);
                });
            }

            return servers;
        } catch (Exception e) {
            throw new McpConfigException(configFile.toString(),
                "Failed to parse MCP config: " + e.getMessage(), e);
        }
    }
    
    /**
     * 保存 MCP 服务器配置
     *
     * @param configFile 配置文件路径
     * @param servers 服务器配置映射
     */
    public void save(Path configFile, Map<String, McpServerConfig> servers) {
        try {
            Map<String, Object> root = new HashMap<>();
            root.put("mcpServers", servers);
            
            objectMapper.writerWithDefaultPrettyPrinter()
                .writeValue(configFile.toFile(), root);
        } catch (Exception e) {
            throw new McpConfigException(configFile.toString(),
                "Failed to save MCP config: " + e.getMessage(), e);
        }
    }
}