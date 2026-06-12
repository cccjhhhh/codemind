package com.codemind.mcp;

import com.codemind.api.mcp.McpConfigException;
import com.codemind.api.mcp.McpServerConfig;
import com.codemind.impl.mcp.McpConfigLoader;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import static org.junit.jupiter.api.Assertions.*;

class McpConfigLoaderTest {

    @TempDir
    Path tempDir;

    @Test
    void testLoadValidConfig() throws Exception {
        String configJson = """
            {
              "mcpServers": {
                "filesystem": {
                  "transport": "stdio",
                  "command": "npx",
                  "args": ["-y", "@modelcontextprotocol/server-filesystem", "/tmp"],
                  "enabled": true
                }
              }
            }
            """;
        Path configFile = tempDir.resolve("mcp.json");
        Files.writeString(configFile, configJson);

        McpConfigLoader loader = new McpConfigLoader();
        Map<String, McpServerConfig> servers = loader.load(configFile);

        assertEquals(1, servers.size());
        assertTrue(servers.containsKey("filesystem"));

        McpServerConfig fsConfig = servers.get("filesystem");
        assertEquals("stdio", fsConfig.getTransport());
        assertEquals("npx", fsConfig.getCommand());
        assertTrue(fsConfig.isEnabled());
    }

    @Test
    void testLoadNonExistentConfig() throws Exception {
        Path configFile = tempDir.resolve("nonexistent.json");

        McpConfigLoader loader = new McpConfigLoader();
        Map<String, McpServerConfig> servers = loader.load(configFile);

        assertTrue(servers.isEmpty());
    }

    @Test
    void testLoadInvalidJson() throws Exception {
        String configJson = "not valid json";
        Path configFile = tempDir.resolve("mcp.json");
        Files.writeString(configFile, configJson);

        McpConfigLoader loader = new McpConfigLoader();
        assertThrows(McpConfigException.class, () -> loader.load(configFile));
    }
}