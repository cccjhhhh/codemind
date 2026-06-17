package com.codemind.mcp;

import java.nio.file.Path;
import java.util.Map;

/**
 * MCP 命令处理逻辑
 * 
 * 被 CLI.java 调用，处理 /mcp 命令。
 */
public class McpCommandHandler {
    
    private final McpConfigLoader configLoader;
    private final Path configPath;
    
    public McpCommandHandler(McpConfigLoader configLoader, Path configPath) {
        this.configLoader = configLoader;
        this.configPath = configPath;
    }
    
    public void handle(String[] args) {
        if (args.length < 2) {
            printHelp();
            return;
        }
        
        String subCommand = args[1];
        switch (subCommand) {
            case "list":
                handleList();
                break;
            case "add":
                handleAdd(args);
                break;
            case "remove":
                if (args.length < 3) {
                    System.out.println("Usage: codemind mcp remove <name>");
                    return;
                }
                handleRemove(args[2]);
                break;
            case "enable":
                if (args.length < 3) {
                    System.out.println("Usage: codemind mcp enable <name>");
                    return;
                }
                handleToggle(args[2], true);
                break;
            case "disable":
                if (args.length < 3) {
                    System.out.println("Usage: codemind mcp disable <name>");
                    return;
                }
                handleToggle(args[2], false);
                break;
            case "config":
                handleConfig();
                break;
            default:
                System.out.println("Unknown subcommand: " + subCommand);
                printHelp();
        }
    }
    
    public void handleList() {
        try {
            Map<String, McpServerConfig> servers = configLoader.load(configPath);
            
            if (servers.isEmpty()) {
                System.out.println("No MCP servers configured.");
                System.out.println("Use 'codemind mcp add' to add a server.");
                return;
            }
            
            System.out.println("MCP Servers:");
            for (Map.Entry<String, McpServerConfig> entry : servers.entrySet()) {
                String name = entry.getKey();
                McpServerConfig config = entry.getValue();
                String status = config.isEnabled() ? "✓" : "✗";
                String transport = config.getTransport();
                
                System.out.printf("  %s %s (%s)%n", status, name, transport);
                
                if ("stdio".equals(transport)) {
                    System.out.printf("    Command: %s %s%n", config.getCommand(), 
                        config.getArgs() != null ? String.join(" ", config.getArgs()) : "");
                } else if ("http-sse".equals(transport)) {
                    System.out.printf("    URL: %s%n", config.getUrl());
                }
            }
            System.out.println();
        } catch (Exception e) {
            System.out.println("Error loading config: " + e.getMessage());
        }
    }
    
    public void handleAdd(String[] args) {
        System.out.println("Interactive MCP server configuration not yet implemented.");
        System.out.println("Please edit ~/.codemind/mcp.json directly.");
    }
    
    public void handleRemove(String name) {
        try {
            Map<String, McpServerConfig> servers = configLoader.load(configPath);
            
            if (!servers.containsKey(name)) {
                System.out.println("Server not found: " + name);
                return;
            }
            
            servers.remove(name);
            configLoader.save(configPath, servers);
            System.out.println("Removed server: " + name);
        } catch (Exception e) {
            System.out.println("Error: " + e.getMessage());
        }
    }
    
    public void handleToggle(String name, boolean enable) {
        try {
            Map<String, McpServerConfig> servers = configLoader.load(configPath);
            
            if (!servers.containsKey(name)) {
                System.out.println("Server not found: " + name);
                return;
            }
            
            servers.get(name).setEnabled(enable);
            configLoader.save(configPath, servers);
            System.out.println(enable ? "Enabled server: " + name : "Disabled server: " + name);
        } catch (Exception e) {
            System.out.println("Error: " + e.getMessage());
        }
    }
    
    public void handleConfig() {
        try {
            Map<String, McpServerConfig> servers = configLoader.load(configPath);
            System.out.println("Config file: " + configPath);
            System.out.println("Servers: " + servers.size());
        } catch (Exception e) {
            System.out.println("Error: " + e.getMessage());
        }
    }
    
    private void printHelp() {
        System.out.println("MCP Commands:");
        System.out.println("  codemind mcp list              List all MCP servers");
        System.out.println("  codemind mcp add               Add MCP server (interactive)");
        System.out.println("  codemind mcp remove <name>     Remove MCP server");
        System.out.println("  codemind mcp enable <name>     Enable MCP server");
        System.out.println("  codemind mcp disable <name>    Disable MCP server");
        System.out.println("  codemind mcp config            Show config info");
    }
}