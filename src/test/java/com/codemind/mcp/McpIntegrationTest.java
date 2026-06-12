package com.codemind.mcp;

import com.codemind.api.mcp.McpServerConfig;
import com.codemind.impl.mcp.McpClientFactoryImpl;
import com.codemind.impl.mcp.McpConfigLoader;
import com.codemind.impl.mcp.McpToolAdapterImpl;
import com.codemind.impl.mcp.McpToolRegistry;
import com.codemind.api.mcp.McpClient;
import com.codemind.api.mcp.McpToolAdapter;
import com.codemind.api.mcp.McpToolDefinition;
import com.codemind.api.tool.Tool;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import static org.junit.jupiter.api.Assertions.*;

/**
 * MCP 模块集成测试
 */
class McpIntegrationTest {
    
    @TempDir
    Path tempDir;
    
    @Test
    void testConfigLoadAndClientCreation() throws Exception {
        // 1. 创建测试配置文件
        String configJson = """
            {
              "mcpServers": {
                "filesystem": {
                  "transport": "stdio",
                  "command": "echo",
                  "args": ["test"],
                  "enabled": true
                }
              }
            }
            """;
        Path configFile = tempDir.resolve("mcp.json");
        Files.writeString(configFile, configJson);
        
        // 2. 加载配置
        McpConfigLoader configLoader = new McpConfigLoader();
        var servers = configLoader.load(configFile);
        
        assertEquals(1, servers.size());
        assertTrue(servers.containsKey("filesystem"));
        
        // 3. 创建客户端
        McpClientFactoryImpl clientFactory = new McpClientFactoryImpl();
        McpClient client = clientFactory.createClient(servers.get("filesystem"));
        
        assertNotNull(client);
        assertFalse(client.isConnected());
    }
    
    @Test
    void testToolAdapterIntegration() throws Exception {
        // 1. 创建适配器
        McpToolAdapter adapter = new McpToolAdapterImpl();
        
        // 2. 创建模拟客户端
        McpClient client = new MockMcpClient("test-server");
        
        // 3. 创建 MCP 工具定义
        McpToolDefinition mcpTool = new McpToolDefinition(
            "read_file",
            "Read a file",
            new com.fasterxml.jackson.databind.ObjectMapper().createObjectNode()
        );
        
        // 4. 适配工具
        Tool tool = adapter.adapt(mcpTool, client);
        
        // 5. 验证
        assertEquals("mcp_test-server_read_file", tool.getName());
        assertEquals("Read a file", tool.getDescription());
    }
    
    @Test
    void testToolRegistryIntegration() {
        // 1. 创建注册表
        McpToolRegistry registry = new McpToolRegistry();
        
        // 2. 创建模拟工具
        Tool tool1 = new MockTool("mcp_fs_read", "Read file");
        Tool tool2 = new MockTool("mcp_fs_write", "Write file");
        
        // 3. 注册工具
        registry.registerServerTools("filesystem", List.of(tool1, tool2));
        
        // 4. 验证
        assertEquals(2, registry.getAllTools().size());
        assertTrue(registry.hasTool("mcp_fs_read"));
        assertTrue(registry.hasTool("mcp_fs_write"));
    }
    
    // 辅助类
    private static class MockMcpClient implements McpClient {
        private final String serverName;
        
        MockMcpClient(String serverName) {
            this.serverName = serverName;
        }
        
        @Override
        public void connect(McpServerConfig config) {}
        
        @Override
        public void disconnect() {}
        
        @Override
        public List<McpToolDefinition> listTools() {
            return List.of();
        }
        
        @Override
        public com.codemind.api.tool.ToolResult executeTool(String toolName, java.util.Map<String, Object> params) {
            return com.codemind.api.tool.ToolResult.success("mock result");
        }
        
        @Override
        public boolean isConnected() {
            return true;
        }
        
        @Override
        public String getServerName() {
            return serverName;
        }
    }
    
    private static class MockTool implements Tool {
        private final String name;
        private final String description;
        
        MockTool(String name, String description) {
            this.name = name;
            this.description = description;
        }
        
        @Override
        public String getName() {
            return name;
        }
        
        @Override
        public String getDescription() {
            return description;
        }
        
        @Override
        public com.fasterxml.jackson.databind.JsonNode getInputSchema() {
            return new com.fasterxml.jackson.databind.ObjectMapper().createObjectNode();
        }
        
        @Override
        public com.codemind.api.tool.ToolResult execute(java.util.Map<String, Object> params) {
            return com.codemind.api.tool.ToolResult.success("mock result");
        }
        
        @Override
        public java.util.Optional<String> getDeprecatedName() {
            return java.util.Optional.empty();
        }
    }
}