package com.codemind.mcp;

import com.codemind.api.mcp.McpServerConfig;
import com.codemind.impl.mcp.McpCommandHandler;
import com.codemind.impl.mcp.McpConfigLoader;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import java.nio.file.Path;
import static org.junit.jupiter.api.Assertions.*;

class McpCommandHandlerTest {
    
    @TempDir
    Path tempDir;
    
    @Test
    void testHandleListEmpty() throws Exception {
        McpConfigLoader configLoader = new McpConfigLoader();
        McpCommandHandler handler = new McpCommandHandler(configLoader, tempDir.resolve("mcp.json"));
        
        // Should not throw
        assertDoesNotThrow(() -> handler.handleList());
    }
    
    @Test
    void testHandleInvalidCommand() throws Exception {
        McpConfigLoader configLoader = new McpConfigLoader();
        McpCommandHandler handler = new McpCommandHandler(configLoader, tempDir.resolve("mcp.json"));
        
        // Should not throw
        assertDoesNotThrow(() -> handler.handle(new String[]{"mcp", "invalid"}));
    }
}