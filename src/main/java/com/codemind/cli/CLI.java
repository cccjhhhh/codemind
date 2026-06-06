package com.codemind.cli;

import com.codemind.api.cli.OutputFormatter;
import com.codemind.api.llm.LLMClient;
import com.codemind.api.llm.ModelConfig;
import com.codemind.api.safety.PermissionGate;
import com.codemind.api.safety.PermissionLevel;
import com.codemind.api.safety.PermissionPrompter;
import com.codemind.api.session.SessionContext;
import com.codemind.api.session.SessionManager;
import com.codemind.impl.session.SessionManagerImpl;
import com.codemind.api.tool.ToolRegistry;
import com.codemind.core.AgentLoop;
import com.codemind.core.AgentResult;
import com.codemind.impl.bootstrap.AppBinder;
import com.codemind.impl.builtin.skill.CodeReviewSkill;
import com.codemind.impl.builtin.skill.LogAnalysisSkill;
import com.codemind.impl.cli.CLIPermissionPrompter;
import com.codemind.impl.cli.DefaultOutputFormatter;
import com.codemind.impl.llm.ModelFactory;
import com.codemind.impl.llm.ModelManager;
import com.codemind.impl.skill.SkillDefinition;
import com.codemind.impl.skill.SkillLoader;
import com.codemind.impl.skill.routing.SkillRouter;
import com.codemind.impl.tool.*;
import picocli.CommandLine;
import picocli.CommandLine.Command;
import picocli.CommandLine.Option;

import java.nio.file.Path;
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
 * 参考 Claude Code：
 * - 启动时自动检测 git 根目录作为项目目录
 * - 支持 -p/--project 参数指定项目目录
 * - LLM 知道工作目录，不会瞎猜路径
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
    
    // 默认最大迭代次数（参考 LangChain AgentExecutor）
    private static final int DEFAULT_MAX_ITERATIONS = 50;
    
    // 默认超时时间（秒）
    private static final int DEFAULT_TIMEOUT_SECONDS = 300;
    
    // ToolRegistry 引用（用于权限管理）
    private ToolRegistry toolRegistry;
    
    // PermissionGate 引用（用于权限检查）
    private PermissionGate permissionGate;
    
    // SessionManager 引用（用于会话管理）
    private SessionManager sessionManager;
    
    // OutputFormatter 引用
    private OutputFormatter outputFormatter = new DefaultOutputFormatter();
    
    // PermissionPrompter 引用
    private PermissionPrompter permissionPrompter = new CLIPermissionPrompter(outputFormatter);
    
    @Option(names = {"-c", "--config"}, description = "配置文件路径")
    private String configPath;
    
    @Option(names = {"-p", "--project"}, description = "项目目录路径（默认自动检测 git 根目录）")
    private String projectPath;
    
    @Option(names = {"-v", "--verbose"}, description = "详细输出")
    private boolean verbose;
    
    @Option(names = {"--max-iterations"}, description = "最大迭代次数（默认 50）")
    private int maxIterations = DEFAULT_MAX_ITERATIONS;
    
    @Option(names = {"--timeout"}, description = "超时时间（秒，默认 300）")
    private int timeoutSeconds = DEFAULT_TIMEOUT_SECONDS;
    
    public static void main(String[] args) {
        int exitCode = new CommandLine(new CLI()).execute(args);
        System.exit(exitCode);
    }
    
    @Override
    public void run() {
        printWelcomeBanner();
        
        // 检测项目目录（参考 Claude Code：使用 git root）
        Path projectDir = detectProjectDirectory();
        
        // 使用 AppBinder 集中管理依赖创建
        AppBinder binder = new AppBinder();
        AppBinder.AppDependencies deps = binder.createDependencies();
        
        // 初始化模型管理器
        ModelManager modelManager = new ModelManager();
        
        // 从依赖配置中获取组件
        ToolRegistry toolRegistry = deps.getToolRegistry();
        SessionManager sessionManager = deps.getSessionManager();
        PermissionGate permissionGate = deps.getPermissionGate();
        
        // 保存引用以便后续使用
        this.toolRegistry = toolRegistry;
        this.permissionGate = permissionGate;
        this.sessionManager = sessionManager;
        
        // 注册所有工具
        toolRegistry.register(new ReadTool());
        toolRegistry.register(new WriteTool());
        toolRegistry.register(new EditTool());
        toolRegistry.register(new GrepTool());
        toolRegistry.register(new BashTool());
        toolRegistry.register(new GlobTool());
        toolRegistry.register(new WebFetchTool());
        toolRegistry.register(new AgentTool());
        
        // 创建会话并设置工作目录
        SessionContext context = sessionManager.createSession();
        context.setWorkingDirectory(projectDir);
        
        // 设置系统消息，告知 LLM 工作目录（参考 Claude Code）
        String systemPrompt = buildSystemPrompt(projectDir);
        context.setSystemMessage(systemPrompt);
        
        // 创建 SkillLoader
        // 正确顺序：先注册 Executor，再加载 Skill 定义
        SkillLoader skillLoader = binder.createSkillLoader();
        
        // 1. 先注册 Executor（Java 实现类）
        // 注意：Executor 名称必须与 SKILL.md 中的 name 字段一致
        skillLoader.registerExecutor("code_review", new CodeReviewSkill());
        skillLoader.registerExecutor("analyze_logs", new LogAnalysisSkill());
        
        // 2. 再从 classpath 加载 Skill 定义（会自动关联已注册的 Executor）
        List<SkillDefinition> skillDefinitions = binder.loadSkillsFromClasspath(skillLoader, null);
        
        // 3. 先获取初始模型并创建 LLMClient（SkillRouter 需要）
        LLMClient llmClient = ModelFactory.create(modelManager.getCurrentModel());
        
        // 4. 创建 SkillRouter（使用语义路由）
        SkillRouter skillRouter = binder.createSkillRouter(llmClient, skillDefinitions);
        
        // 5. 创建 AgentLoop
        AgentLoop agentLoop = new AgentLoop(llmClient, toolRegistry, permissionGate, outputFormatter, maxIterations, timeoutSeconds, skillRouter);
        
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
                        agentLoop = new AgentLoop(llmClient, toolRegistry, permissionGate, outputFormatter, maxIterations, timeoutSeconds, skillRouter);
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
                // 保存当前会话
                sessionManager.closeSession(context.getSessionId());
                System.out.println(YELLOW + "会话已保存，再见！" + RESET);
                break;
            }
            
            // 调用 Agent（流式输出）
            System.out.println();
            AgentResult result = agentLoop.runStream(input, context, token -> {
                // 流式输出：实时打印 token
                System.out.print(token);
                System.out.flush();
            });
            
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
        System.out.println(CYAN + BOLD + "║              CodeMind                  ║" + RESET);
        System.out.println(CYAN + BOLD + "║             Version 1.0.0              ║" + RESET);
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
                
            case "/sessions":
                handleSessionsCommand();
                return null;
                
            case "/load":
                handleLoadCommand(parts, scanner);
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
        System.out.println("  " + CYAN + "/sessions" + RESET + "        列出已保存的会话");
        System.out.println("  " + CYAN + "/load [id]" + RESET + "       加载指定会话");
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
            System.out.println(YELLOW + "可授权的工具:" + RESET);
            System.out.println("  " + CYAN + "write" + RESET + "           写入文件");
            System.out.println("  " + CYAN + "bash" + RESET + "            执行命令");
            System.out.println("  " + CYAN + "all" + RESET + "            授权所有工具");
            System.out.println();
            System.out.println("用法: " + CYAN + "/allow <工具名>" + RESET);
            System.out.println();
            return;
        }

        String toolName = parts[1];

        if ("ALL".equalsIgnoreCase(toolName)) {
            // 授权所有工具
            for (String name : ((com.codemind.impl.tool.ToolRegistryImpl) toolRegistry).getToolNames()) {
                permissionGate.setDefaultLevel(name, PermissionLevel.ALLOW);
            }
            System.out.println();
            System.out.println(GREEN + BOLD + "✓ 已授权所有工具" + RESET);
            System.out.println();
        } else {
            permissionGate.setDefaultLevel(toolName, PermissionLevel.ALLOW);
            System.out.println();
            System.out.println(GREEN + BOLD + "✓ 已授权: " + toolName + RESET);
            System.out.println();
        }
    }
    
    /**
     * 打印权限状态
     */
    private void printPermissionsStatus() {
        System.out.println();
        System.out.println(BOLD + "权限状态:" + RESET);
        System.out.println();

        com.codemind.impl.tool.ToolRegistryImpl registry = (com.codemind.impl.tool.ToolRegistryImpl) toolRegistry;
        for (String toolName : registry.getToolNames()) {
            PermissionLevel level = permissionGate.getDefaultLevel(toolName);

            String status;
            switch (level) {
                case ALLOW:
                    status = GREEN + "✓ 已授权" + RESET;
                    break;
                case ASK:
                    status = YELLOW + "⚠ 需确认" + RESET;
                    break;
                case DENY:
                default:
                    status = RED + "✗ 禁止" + RESET;
                    break;
            }

            System.out.println("  " + toolName + RESET + " " + status);
        }
        System.out.println();
        System.out.println(DIM + "提示: 使用 /allow <工具名> 授权操作" + RESET);
        System.out.println();
    }
    
    /**
     * 检测项目目录
     * 
     * 逻辑（参考 Claude Code）：
     * 1. 如果用户指定了 -p/--project 参数，使用指定目录
     * 2. 否则，使用 CLI 启动时的当前目录（user.dir）
     * 
     * 注意：不需要找 git root，Claude Code 也是直接用当前目录。
     * 
     * @return 项目目录路径
     */
    private Path detectProjectDirectory() {
        Path startDir = Path.of(System.getProperty("user.dir")).toAbsolutePath();
        
        if (projectPath != null && !projectPath.isEmpty()) {
            // 用户指定了项目目录
            Path userDir = Path.of(projectPath).toAbsolutePath();
            if (!userDir.toFile().exists()) {
                System.out.println(YELLOW + "警告: 指定的项目目录不存在: " + projectPath + RESET);
                System.out.println(YELLOW + "使用当前目录代替" + RESET);
                return startDir;
            }
            System.out.println(DIM + "使用指定项目目录: " + userDir + RESET);
            return userDir;
        }

        // 直接使用 CLI 启动时的当前目录
        System.out.println(DIM + "工作目录: " + startDir + RESET);
        return startDir;
    }
    
    /**
     * 构建系统提示词
     * 
     * 参考 Claude Code：告知 LLM 工作目录，避免瞎猜路径
     * 
     * @param projectDir 项目目录
     * @return 系统提示词
     */
    private String buildSystemPrompt(Path projectDir) {
        return "工作目录: " + projectDir.toAbsolutePath() + "\n" +
               "操作系统: " + System.getProperty("os.name") + "\n" +
               "注意: 执行命令时默认已在工作目录下，无需 cd。\n\n" +
               "可用技能（优先使用这些，而不是自己拼凑命令）：\n" +
               "- code_review: 当你需要审查代码时使用\n" +
               "- analyze_logs: 当你需要分析日志时使用\n";
    }
    
    /**
     * 处理 /sessions 命令 - 列出已保存的会话
     */
    private void handleSessionsCommand() {
        System.out.println();
        System.out.println(BOLD + "已保存的会话:" + RESET);
        System.out.println();
        
        if (!(sessionManager instanceof com.codemind.impl.session.SessionManagerImpl)) {
            System.out.println(DIM + "会话管理不支持持久化" + RESET);
            System.out.println();
            return;
        }
        
        com.codemind.impl.session.SessionManagerImpl sessionManagerImpl = 
            (com.codemind.impl.session.SessionManagerImpl) sessionManager;
        java.util.List<com.codemind.dto.session.SessionInfoDto> sessions = sessionManagerImpl.listSavedSessions();
        
        if (sessions.isEmpty()) {
            System.out.println(DIM + "暂无已保存的会话" + RESET);
            System.out.println();
            return;
        }
        
        System.out.println("  " + DIM + "ID" + RESET + "                    " + DIM + "创建时间" + RESET + "          " + DIM + "最后活跃" + RESET + "      " + DIM + "消息数" + RESET);
        System.out.println("  " + "-".repeat(80));
        
        for (com.codemind.dto.session.SessionInfoDto session : sessions) {
            System.out.printf("  %-24s %s    %s    %d%n",
                session.getSessionId().substring(0, Math.min(8, session.getSessionId().length())) + "...",
                session.getCreatedAt().toString().substring(0, 16),
                session.getLastActiveAt().toString().substring(0, 16),
                session.getMessageCount());
        }
        System.out.println();
        System.out.println("使用 " + CYAN + "/load <session_id>" + RESET + " 加载会话");
        System.out.println();
    }
    
    /**
     * 处理 /load 命令 - 加载指定会话
     */
    private void handleLoadCommand(String[] parts, Scanner scanner) {
        if (parts.length < 2) {
            System.out.println();
            System.out.println(YELLOW + "用法: /load <session_id>" + RESET);
            System.out.println("使用 " + CYAN + "/sessions" + RESET + " 查看可用会话");
            System.out.println();
            return;
        }
        
        String sessionId = parts[1];
        
        if (!(sessionManager instanceof com.codemind.impl.session.SessionManagerImpl)) {
            System.out.println(RED + "错误: 会话管理不支持持久化" + RESET);
            System.out.println();
            return;
        }
        
        com.codemind.impl.session.SessionManagerImpl sessionManagerImpl = 
            (com.codemind.impl.session.SessionManagerImpl) sessionManager;
        
        // 尝试加载会话
        com.codemind.api.session.SessionContext loadedContext = sessionManagerImpl.loadSession(sessionId);
        
        if (loadedContext == null) {
            System.out.println(RED + "错误: 会话不存在或加载失败: " + sessionId + RESET);
            System.out.println("使用 " + CYAN + "/sessions" + RESET + " 查看可用会话");
            System.out.println();
            return;
        }
        
        // 更新当前上下文
        // 注意：这会替换当前的 context
        System.out.println(GREEN + "✓ 已加载会话: " + sessionId + RESET);
        System.out.println();
    }
}