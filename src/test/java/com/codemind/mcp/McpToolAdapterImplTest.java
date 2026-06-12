package com.codemind.mcp;

import com.codemind.api.mcp.McpClient;
import com.codemind.api.mcp.McpToolAdapter;
import com.codemind.api.mcp.McpToolDefinition;
import com.codemind.api.tool.Tool;
import com.codemind.impl.mcp.McpToolAdapterImpl;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import static org.junit.jupiter.api.Assertions.*;

class McpToolAdapterImplTest {
    
    @Test
    void testGeneratePrefixedName() {
        McpToolAdapter adapter = new McpToolAdapterImpl();
        String prefixed = adapter.generatePrefixedName("filesystem", "read_file");
        assertEquals("mcp_filesystem_read_file", prefixed);
    }
    
    @Test
    void testAdaptTool() throws Exception {
        McpToolAdapter adapter = new McpToolAdapterImpl();
        McpClient client = Mockito.mock(McpClient.class);
        Mockito.when(client.getServerName()).thenReturn("filesystem");
        
        ObjectMapper mapper = new ObjectMapper();
        ObjectNode inputSchema = mapper.createObjectNode();
        inputSchema.put("type", "object");
        
        McpToolDefinition mcpTool = new McpToolDefinition(
            "read_file",
            "Read a file from the filesystem",
            inputSchema
        );
        
        Tool tool = adapter.adapt(mcpTool, client);
        
        assertNotNull(tool);
        assertEquals("mcp_filesystem_read_file", tool.getName());
        assertEquals("Read a file from the filesystem", tool.getDescription());
    }
}