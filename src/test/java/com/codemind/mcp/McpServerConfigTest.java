package com.codemind.mcp;

import com.codemind.api.mcp.McpServerConfig;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class McpServerConfigTest {
    
    @Test
    void testStdioConfig() {
        McpServerConfig config = new McpServerConfig();
        config.setTransport("stdio");
        config.setCommand("npx");
        config.setArgs(new String[]{"-y", "@modelcontextprotocol/server-filesystem", "/tmp"});
        config.setEnabled(true);
        
        assertEquals("stdio", config.getTransport());
        assertEquals("npx", config.getCommand());
        assertArrayEquals(new String[]{"-y", "@modelcontextprotocol/server-filesystem", "/tmp"}, config.getArgs());
        assertTrue(config.isEnabled());
        assertNull(config.getUrl());
        assertNull(config.getHeaders());
    }
    
    @Test
    void testHttpSseConfig() {
        McpServerConfig config = new McpServerConfig();
        config.setTransport("http-sse");
        config.setUrl("https://mcp.example.com/sse");
        config.setEnabled(true);
        
        assertEquals("http-sse", config.getTransport());
        assertEquals("https://mcp.example.com/sse", config.getUrl());
        assertNull(config.getCommand());
        assertNull(config.getArgs());
    }
}
