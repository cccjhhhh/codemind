package com.codemind.mcp;

import com.codemind.api.mcp.McpClient;
import com.codemind.api.mcp.McpConnectionException;
import com.codemind.api.mcp.McpServerConfig;
import com.codemind.impl.mcp.McpClientImpl;
import com.codemind.impl.mcp.McpTransportFactory;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import static org.junit.jupiter.api.Assertions.*;

class McpClientImplTest {
    
    @Test
    void testInitialState() {
        McpTransportFactory transportFactory = Mockito.mock(McpTransportFactory.class);
        McpClient client = new McpClientImpl(transportFactory);
        
        assertFalse(client.isConnected());
        assertNull(client.getServerName());
    }
    
    @Test
    void testDisconnectWhenNotConnected() {
        McpTransportFactory transportFactory = Mockito.mock(McpTransportFactory.class);
        McpClient client = new McpClientImpl(transportFactory);
        
        // Should not throw
        assertDoesNotThrow(() -> client.disconnect());
    }
}