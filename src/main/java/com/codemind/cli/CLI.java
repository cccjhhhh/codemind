package com.codemind.cli;

import com.codemind.api.cli.OutputFormatter;
import com.codemind.api.llm.LLMClient;
import com.codemind.api.llm.ModelConfig;
import com.codemind.api.safety.Permission;
import com.codemind.api.safety.PermissionGate;
import com.codemind.api.safety.PermissionPrompter;
import com.codemind.api.session.SessionContext;
import com.codemind.api.session.SessionManager;
import com.codemind.core.AgentLoop;
import com.codemind.core.AgentResult;
import com.codemind.impl.bootstrap.AppBinder;
import com.codemind.impl.cli.CLIPermissionPrompter;
import com.codemind.impl.cli.DefaultOutputFormatter;
import com.codemind.impl.llm.ModelFactory;
import com.codemind.impl.llm.ModelManager;
import com.codemind.impl.tool.*;
import picocli.CommandLine;
import picocli.CommandLine.Command;
import picocli.CommandLine.Option;

import java.util.List;
import java.util.Scanner;

import static com.codemind.impl.cli.AnsiStyles.*;

/**
 * CodeMind 命令行界面
 * 
 * 提供 ANSI 颜色美化的交互式界面
 * 
 * 设计原则：
 * - 单一职责原则（SRP）：只负责命令解析和用户交互
 * - 依赖倒置原则（DIP）：依赖接口而非实现
 * 
 * 注意：ANSI 颜色常量已移至 AnsiStyles 类，
 *       CLIPermissionPrompter 已提取为独立类。
 */
@Command(
    name = "codemind",
    description = "智能编程助手",
    mixinStandardHelpOptions = true,
    version = "1.0.0"
)
public class CLI implements Runnable {
    
    // API Key 占位符（无效的标志）
    private static final String PLACEHOLDER_KEY_PATTERN = "YOUR_.*_API_KEY";
    
    // ToolRegistry 引用（用于权限管理）
    private ToolRegistryImpl toolRegistry;
    
    // PermissionGate 引用（用于权限检查）
    private PermissionGate permissionGate;
    
    // OutputFormatter 引用
    private OutputFormatter outputFormatter = new DefaultOutputFormatter();
    
    // PermissionPrompter 引用
    private PermissionPrompter permissionPrompter = new CLIPermissionPrompter(outputFormatter);
    
    @Option(names = {"-c", "--config"}, description = "配置文件路径")
    private String configPath;
    
    @Option(names = {"-v", "--verbose"}, description = "详细输出")
    private boolean verbose;
    
    public static void main(String[] args) {
        int exitCode = new CommandLine(new CLI()).execute(args);
        System.exit(exitCode);
    }
    
    @Override
    public void run() {
        printWelcomeBanner();
        
        // 使用 AppBinder 集中管理依赖创建
        AppBinder binder = new AppBinder();
        AppBinder.AppDependencies deps = binder.createDependencies();
        
        // 初始化模型管理器
        ModelManager modelManager = new ModelManager();
        
        // 从依赖配置中获取组件
        ToolRegistryImpl toolRegistry = (ToolRegistryImpl) deps.getToolRegistry();
        SessionManager sessionManager = deps.getSessionManager();
        PermissionGate permissionGate = deps.getPermissionGate();
        
        // 保存引用以便后续使用
        this.toolRegistry = toolRegistry;
        this.permissionGate = permissionGate;
        
        // 注册所有工具
        toolRegistry.register(new FileReaderTool());
        toolRegistry.register(new FileWriterTool());
        toolRegistry.register(new CodeSearchTool());
        toolRegistry.register(new CommandRunnerTool());
        toolRegistry.register(new LogParserTool());
        
        // 创建会话
        SessionContext context = sessionManager.createSession();
        
        // 获取初始模型并创建 LLMClient
        LLMClient llmClient = ModelFactory.create(modelManager.getCurrentModel());
        AgentLoop agentLoop = new AgentLoop(llmClient, toolRegistry, permissionGate, outputFormatter, 10);
        
        // 显示当前模型
        System.out.println(GREEN + "当前模型: " + modelManager.getCurrentModel().getName() + RESET);
        System.out.println();
        System.out.println("欢迎使用 CodeMind！");
        System.out.println("命令: " + CYAN + "/models" + RESET + " 查看模型, " + 
                           CYAN + "/switch" + RESET + " 切换模型, " + 
                           CYAN + "/help" + RESET + " 帮助, " + 
                           DIM + "quit" + RESET + " 退出");
        System.out.println();
        
        // 交互式循环
        Scanner scanner = new Scanner(System.in);
        while (true) {
            // 显示带模型信息的提示符
            printPrompt(modelManager);
            String input = scanner.nextLine();
            
            if (input == null || input.trim().isEmpty()) {
                continue;
            }
            
            // 处理命令
            if (input.startsWith("/")) {
                // 检查是否需要切换模型（返回新的 ModelConfig 表示需要更新）
                ModelConfig newModel = handleCommand(input, modelManager, scanner);
                if (newModel != null) {
                    // 模型已切换，重新创建客户端
                    try {
                        llmClient = ModelFactory.create(newModel);
                        agentLoop = new AgentLoop(llmClient, toolRegistry, permissionGate, outputFormatter, 10);
                        System.out.println(GREEN + "✓ 模型切换成功！" + RESET);
                        System.out.println();
                    } catch (Exception e) {
                        System.out.println(RED + "创建模型客户端失败: " + e.getMessage() + RESET);
                        // 切换回原来的模型
                        modelManager.switchModel(modelManager.getCurrentModelId());
                    }
                }
                continue;
            }
            
            if ("quit".equalsIgnoreCase(input) || "exit".equalsIgnoreCase(input)) {
                System.out.println(YELLOW + "再见！" + RESET);
                break;
            }
            
            // 调用 Agent（流式输出）
            System.out.println();
            AgentResult result = agentLoop.runStream(input, context, token -> {
                // 流式输出：实时打印 token
                System.out.print(token);
                System.out.flush();
            }, permissionPrompter);
            
            // 确保换行
            System.out.println();
            
            // 检查结果
            if (!result.isSuccess()) {
                System.out.println(RED + "错误: " + result.getError() + RESET);
            }
            System.out.println();
        }
    }
    
    /**
     * 打印欢迎 Banner
     */
    private void printWelcomeBanner() {
        System.out.println();
        System.out.println(CYAN + BOLD + "╔════════════════════════════════════════╗" + RESET);
        System.out.println(CYAN + BOLD + "║       CodeMind - 智能编程助手           ║" + RESET);
        System.out.println(CYAN + BOLD + "║       Version 1.0.0                    ║" + RESET);
        System.out.println(CYAN + BOLD + "╚════════════════════════════════════════╝" + RESET);
        System.out.println();
    }
    
    /**
     * 打印带模型信息的提示符
     */
    private void printPrompt(ModelManager modelManager) {
        String modelId = modelManager.getCurrentModelId();
        System.out.print(BOLD + BLUE + "codemind" + RESET + " " + 
                         DIM + "[" + YELLOW + modelId + DIM + "]" + RESET + " " +
                         GREEN + ">>> " + RESET);
    }
    
    /**
     * 处理命令
     * 
     * @return 如果模型被切换，返回新的 ModelConfig；否则返回 null
     */
    private ModelConfig handleCommand(String input, ModelManager modelManager, Scanner scanner) {
        String[] parts = input.trim().split("\\s+");
        String command = parts[0];
        
        switch (command) {
            case "/models":
                printModelsList(modelManager);
                return null;
                
            case "/switch":
                return handleSwitchCommand(modelManager, scanner);
                
            case "/allow":
                handleAllowCommand(parts);
                return null;
                
            case "/permissions":
                printPermissionsStatus();
                return null;
                
            case "/help":
                printHelp();
                return null;
                
            default:
                System.out.println(RED + "未知命令: " + command + RESET);
                System.out.println("输入 " + CYAN + "/help" + RESET + " 查看可用命令");
                return null;
        }
    }
    
    /**
     * 打印模型列表
     */
    private void printModelsList(ModelManager modelManager) {
        System.out.println();
        System.out.println(BOLD + "可用模型:" + RESET);
        List<ModelConfig> models = modelManager.listModels();
        for (int i = 0; i < models.size(); i++) {
            ModelConfig model = models.get(i);
            String currentMarker = model.getId().equals(modelManager.getCurrentModelId()) 
                ? " " + GREEN + "✓ 当前" + RESET 
                : "";
            String apiStatus = checkApiKeyStatus(model);
            System.out.println("  " + DIM + "[" + (i + 1) + "]" + RESET + " " + 
                               BOLD + model.getName() + RESET + 
                               DIM + " (" + model.getId() + ")" + RESET + 
                               currentMarker +
                               apiStatus);
        }
        System.out.println();
    }
    
    /**
     * 检查 API Key 状态并返回提示文本
     */
    private String checkApiKeyStatus(ModelConfig model) {
        String apiKey = model.getApiKey();
        if (apiKey == null || apiKey.isEmpty() || apiKey.matches(PLACEHOLDER_KEY_PATTERN)) {
            return " " + YELLOW + "⚠ 未配置" + RESET;
        }
        return " " + GREEN + "✓ 已配置" + RESET;
    }
    
    /**
     * 检查 API Key 是否有效
     */
    private boolean isApiKeyValid(ModelConfig model) {
        String apiKey = model.getApiKey();
        return apiKey != null && 
               !apiKey.isEmpty() && 
               !apiKey.matches(PLACEHOLDER_KEY_PATTERN);
    }
    
    /**
     * 处理模型切换命令
     * 
     * @return 如果模型被切换，返回新的 ModelConfig；否则返回 null
     */
    private ModelConfig handleSwitchCommand(ModelManager modelManager, Scanner scanner) {
        List<ModelConfig> models = modelManager.listModels();
        
        // 先显示模型列表
        printModelsList(modelManager);
        
        // 提示用户选择
        System.out.print(YELLOW + "请选择模型编号 (1-" + models.size() + ") 或输入模型 ID: " + RESET);
        String selection = scanner.nextLine().trim();
        
        if (selection.isEmpty()) {
            System.out.println(DIM + "已取消切换" + RESET);
            return null;
        }
        
        ModelConfig selected = null;
        
        try {
            // 尝试解析为编号
            int index = Integer.parseInt(selection) - 1;
            if (index >= 0 && index < models.size()) {
                selected = models.get(index);
            } else {
                System.out.println(RED + "无效的编号: " + selection + RESET);
                return null;
            }
        } catch (NumberFormatException e) {
            // 尝试解析为模型 ID
            selected = models.stream()
                .filter(m -> m.getId().equals(selection))
                .findFirst()
                .orElse(null);
            if (selected == null) {
                System.out.println(RED + "未知的模型 ID: " + selection + RESET);
                return null;
            }
        }
        
        // 检查是否是当前模型
        if (selected.getId().equals(modelManager.getCurrentModelId())) {
            System.out.println(DIM + "当前已是该模型，无需切换" + RESET);
            return null;
        }
        
        // 检查 API Key 是否有效
        if (!isApiKeyValid(selected)) {
            System.out.println();
            System.out.println(RED + "✗ 切换失败: " + selected.getName() + " 的 API Key 未配置或无效" + RESET);
            System.out.println(DIM + "  请在 ~/.codemind/models.yml 中配置有效的 API Key" + RESET);
            System.out.println();
            return null;
        }
        
        // 执行切换
        return performSwitch(modelManager, selected);
    }
    
    /**
     * 执行模型切换
     * 
     * @return 切换后的 ModelConfig
     */
    private ModelConfig performSwitch(ModelManager modelManager, ModelConfig selected) {
        try {
            modelManager.switchModel(selected.getId());
            System.out.println();
            System.out.println(GREEN + BOLD + "✓ 已切换到: " + selected.getName() + RESET);
            System.out.println();
            return selected;
        } catch (IllegalArgumentException e) {
            System.out.println(RED + "错误: " + e.getMessage() + RESET);
            return null;
        }
    }
    
    /**
     * 打印帮助信息
     */
    private void printHelp() {
        System.out.println();
        System.out.println(BOLD + "命令列表:" + RESET);
        System.out.println();
        System.out.println("  " + CYAN + "/models" + RESET + "          列出所有可用模型");
        System.out.println("  " + CYAN + "/switch" + RESET + "          切换模型（交互式选择）");
        System.out.println("  " + CYAN + "/allow <权限>" + RESET + "   授权危险操作（write_file, execute_command）");
        System.out.println("  " + CYAN + "/permissions" + RESET + "     显示权限状态");
        System.out.println("  " + CYAN + "/help" + RESET + "            显示帮助");
        System.out.println("  " + DIM + "quit / exit" + RESET + "      退出程序");
        System.out.println();
        System.out.println("提示: 直接输入问题即可与 AI 对话");
        System.out.println();
    }
    
    /**
     * 处理授权命令
     */
    private void handleAllowCommand(String[] parts) {
        if (parts.length < 2) {
            System.out.println();
            System.out.println(YELLOW + "可授权的权限:" + RESET);
            System.out.println("  " + CYAN + "write_file" + RESET + "      写入文件");
            System.out.println("  " + CYAN + "execute_command" + RESET + " 执行命令");
            System.out.println("  " + CYAN + "all" + RESET + "           授权所有危险操作");
            System.out.println();
            System.out.println("用法: " + CYAN + "/allow <权限>" + RESET);
            System.out.println();
            return;
        }
        
        String permissionName = parts[1].toUpperCase();
        
        try {
            if ("ALL".equals(permissionName)) {
                // 授权所有危险操作
                toolRegistry.grantPermission(Permission.WRITE_FILE);
                toolRegistry.grantPermission(Permission.EXECUTE_COMMAND);
                System.out.println();
                System.out.println(GREEN + BOLD + "✓ 已授权所有危险操作" + RESET);
                System.out.println();
            } else {
                Permission permission = Permission.valueOf(permissionName);
                toolRegistry.grantPermission(permission);
                System.out.println();
                System.out.println(GREEN + BOLD + "✓ 已授权: " + permission.getDescription() + RESET);
                System.out.println();
            }
        } catch (IllegalArgumentException e) {
            System.out.println(RED + "未知的权限: " + parts[1] + RESET);
            System.out.println("可用权限: write_file, execute_command, all");
        }
    }
    
    /**
     * 打印权限状态
     */
    private void printPermissionsStatus() {
        System.out.println();
        System.out.println(BOLD + "权限状态:" + RESET);
        System.out.println();
        
        Permission[] permissions = Permission.values();
        for (Permission perm : permissions) {
            boolean needsConfirm = permissionGate.needsConfirmation(perm);
            boolean hasPermission = permissionGate.hasPermission(perm);
            
            String status;
            if (hasPermission) {
                status = GREEN + "✓ 已授权" + RESET;
            } else if (needsConfirm) {
                status = YELLOW + "⚠ 需授权" + RESET;
            } else {
                status = DIM + "○ 默认允许" + RESET;
            }
            
            System.out.println("  " + perm.name() + RESET + " " + 
                               DIM + "(" + perm.getDescription() + ")" + RESET + 
                               " " + status);
        }
        System.out.println();
        System.out.println(DIM + "提示: 使用 /allow <权限> 授权危险操作" + RESET);
        System.out.println();
    }
}