package com.codemind.impl.cli;

import com.codemind.api.cli.OutputFormatter;
import com.codemind.api.safety.Permission;
import com.codemind.api.tool.ToolResult;
import java.util.Map;

public class DefaultOutputFormatter implements OutputFormatter {
    
    @Override
    public String formatToolCallStart(String toolName, Map<String, Object> params) {
        StringBuilder sb = new StringBuilder();
        sb.append(AnsiStyles.DIM)
          .append("┌─ ")
          .append(AnsiStyles.CYAN)
          .append(toolName)
          .append(AnsiStyles.RESET)
          .append("\n");
        
        params.forEach((key, value) -> {
            String valueStr = AnsiStyles.truncate(String.valueOf(value), 60);
            sb.append(AnsiStyles.DIM)
              .append("│ ")
              .append(AnsiStyles.YELLOW)
              .append(key)
              .append(AnsiStyles.RESET)
              .append(": ")
              .append(valueStr)
              .append("\n");
        });
        
        sb.append(AnsiStyles.DIM)
          .append("└─")
          .append(AnsiStyles.RESET);
        
        return sb.toString();
    }
    
    @Override
    public String formatToolCallEnd(String toolName, ToolResult result) {
        if (result.isSuccess()) {
            return " " + AnsiStyles.GREEN + "✓" + AnsiStyles.RESET + "\n";
        } else {
            return " " + AnsiStyles.RED + "✗ " + result.getError() + AnsiStyles.RESET + "\n";
        }
    }
    
    @Override
    public String formatPermissionPrompt(Permission permission, String context) {
        return "\n" 
             + AnsiStyles.BG_YELLOW + AnsiStyles.BLACK + " ⚠ 需要权限 " + AnsiStyles.RESET + " "
             + AnsiStyles.BOLD + permission.getDescription() + AnsiStyles.RESET + "\n"
             + AnsiStyles.DIM + context + AnsiStyles.RESET + "\n"
             + "是否允许？" + AnsiStyles.BOLD + "[y/n/session]" + AnsiStyles.RESET + ": ";
    }
    
    @Override
    public String formatError(String message) {
        return AnsiStyles.RED + "✗ " + message + AnsiStyles.RESET + "\n";
    }
    
    @Override
    public String formatSuccess(String message) {
        return AnsiStyles.GREEN + "✓ " + message + AnsiStyles.RESET + "\n";
    }
}