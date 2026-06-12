package com.codemind.mcp;

import com.codemind.api.tool.Tool;
import com.codemind.api.tool.ToolResult;
import com.codemind.impl.mcp.McpToolRegistry;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import java.util.Arrays;
import java.util.Map;
import static org.junit.jupiter.api.Assertions.*;

class McpToolRegistryTest {

    @Test
    void testRegisterAndRetrieveTools() {
        McpToolRegistry registry = new McpToolRegistry();

        Tool tool1 = createMockTool("mcp_fs_read", "Read file");
        Tool tool2 = createMockTool("mcp_fs_write", "Write file");

        registry.registerServerTools("filesystem", Arrays.asList(tool1, tool2));

        assertEquals(2, registry.getAllTools().size());
        assertTrue(registry.hasTool("mcp_fs_read"));
        assertTrue(registry.hasTool("mcp_fs_write"));
        assertFalse(registry.hasTool("mcp_nonexistent"));
    }

    @Test
    void testUnregisterServerTools() {
        McpToolRegistry registry = new McpToolRegistry();

        Tool tool1 = createMockTool("mcp_fs_read", "Read file");
        registry.registerServerTools("filesystem", Arrays.asList(tool1));

        assertEquals(1, registry.getAllTools().size());

        registry.unregisterServerTools("filesystem");

        assertEquals(0, registry.getAllTools().size());
        assertFalse(registry.hasTool("mcp_fs_read"));
    }

    @Test
    void testExecuteTool() {
        McpToolRegistry registry = new McpToolRegistry();

        Tool tool = createMockTool("mcp_fs_read", "Read file");
        Mockito.when(tool.execute(Mockito.anyMap())).thenReturn(ToolResult.success("content"));

        registry.registerServerTools("filesystem", Arrays.asList(tool));

        ToolResult result = registry.executeTool("mcp_fs_read", Map.of("path", "/tmp/test.txt"));

        assertNotNull(result);
        assertTrue(result.isSuccess());
    }

    private Tool createMockTool(String name, String description) {
        Tool tool = Mockito.mock(Tool.class);
        Mockito.when(tool.getName()).thenReturn(name);
        Mockito.when(tool.getDescription()).thenReturn(description);
        return tool;
    }
}
