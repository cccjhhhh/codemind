package com.codemind.impl.tool;

import com.codemind.api.tool.Tool;
import com.codemind.api.tool.ToolResult;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;

import java.io.BufferedReader;
import java.io.File;
import java.io.InputStreamReader;
import java.util.Map;

/**
 * 命令执行工具
 * 
 * 学习要点：
 * - 进程执行与结果收集
 * - 安全沙箱设计
 * - 超时处理
 * - 工作目录设置
 */
public class CommandRunnerTool implements Tool {
    
    private static final ObjectMapper JSON = new ObjectMapper();
    private static final int DEFAULT_TIMEOUT_SECONDS = 30;
    
    @Override
    public String getName() {
        return "execute_command";
    }
    
    @Override
    public String getDescription() {
        return "执行 shell 命令并返回输出结果。\n" +
               "注意：命令会在工作目录下执行。\n" +
               "Windows 系统使用 'cmd /c'，Unix 系统使用 'sh -c'。";
    }
    
    @Override
    public JsonNode getInputSchema() {
        ObjectNode schema = JSON.createObjectNode();
        schema.put("type", "object");
        
        ObjectNode properties = schema.putObject("properties");
        
        ObjectNode cmdProp = properties.putObject("command");
        cmdProp.put("type", "string");
        cmdProp.put("description", "要执行的命令");
        
        ObjectNode timeoutProp = properties.putObject("timeout");
        timeoutProp.put("type", "integer");
        timeoutProp.put("description", "超时时间（秒）");
        
        ObjectNode cwdProp = properties.putObject("cwd");
        cwdProp.put("type", "string");
        cwdProp.put("description", "工作目录（可选）");
        
        schema.putArray("required").add("command");
        
        return schema;
    }
    
    @Override
    public ToolResult execute(Map<String, Object> params) {
        try {
            String command = (String) params.get("command");
            if (command == null || command.isEmpty()) {
                return ToolResult.failure("参数 'command' 是必需的");
            }
            
            // TODO: 安全检查 - 应该检查命令是否在允许列表中
            
            Integer timeout = (Integer) params.get("timeout");
            int timeoutSeconds = timeout != null ? timeout : DEFAULT_TIMEOUT_SECONDS;
            
            String cwd = (String) params.get("cwd");
            File workingDir = null;
            
            // 设置工作目录
            if (cwd != null && !cwd.isEmpty()) {
                workingDir = new File(cwd);
                System.out.println("[DEBUG] CommandRunnerTool: cwd=" + workingDir.getAbsolutePath());
            }
            
            ProcessBuilder pb = new ProcessBuilder();
            
            // Windows vs Unix 命令处理
            if (System.getProperty("os.name").toLowerCase().contains("win")) {
                pb.command("cmd", "/c", command);
            } else {
                pb.command("sh", "-c", command);
            }
            
            // 设置工作目录（如果指定）
            if (workingDir != null) {
                pb.directory(workingDir);
            }
            
            pb.redirectErrorStream(true);
            
            System.out.println("[DEBUG] Executing command: " + command);
            
            Process process = pb.start();
            
            StringBuilder output = new StringBuilder();
            try (BufferedReader reader = new BufferedReader(
                    new InputStreamReader(process.getInputStream()))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    output.append(line).append("\n");
                }
            }
            
            boolean finished = process.waitFor(timeoutSeconds, java.util.concurrent.TimeUnit.SECONDS);
            
            if (!finished) {
                process.destroyForcibly();
                return ToolResult.failure("命令执行超时");
            }
            
            int exitCode = process.exitValue();
            if (exitCode != 0) {
                return ToolResult.failure("命令执行失败（退出码: " + exitCode + "）\n输出: " + output);
            }
            
            System.out.println("[DEBUG] Command output: " + output.toString().trim());
            
            return ToolResult.success(output.toString());
            
        } catch (Exception e) {
            return ToolResult.failure("命令执行异常: " + e.getMessage());
        }
    }
}
