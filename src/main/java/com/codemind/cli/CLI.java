package com.codemind.cli;

import picocli.CommandLine;
import picocli.CommandLine.Command;
import picocli.CommandLine.Option;

import java.util.concurrent.Callable;

/**
 * CodeMind 命令行界面
 */
@Command(
    name = "codemind",
    description = "智能编程助手",
    mixinStandardHelpOptions = true,
    version = "1.0.0"
)
public class CLI implements Callable<Integer> {
    
    @Option(names = {"-c", "--config"}, description = "配置文件路径")
    private String configPath;
    
    @Option(names = {"-v", "--verbose"}, description = "详细输出")
    private boolean verbose;
    
    @Override
    public Integer call() throws Exception {
        System.out.println("╔════════════════════════════════════════╗");
        System.out.println("║       CodeMind - 智能编程助手           ║");
        System.out.println("║       Version 1.0.0                    ║");
        System.out.println("╚════════════════════════════════════════╝");
        System.out.println();
        System.out.println("欢迎使用 CodeMind！输入 'help' 查看可用命令。");
        System.out.println();
        
        // TODO: 启动交互式 REPL
        startREPL();
        
        return 0;
    }
    
    private void startREPL() {
        // TODO: 实现交互式命令行
        System.out.println("[待实现] 交互式命令行模式");
    }
    
    public int execute(String[] args) {
        return new CommandLine(this).execute(args);
    }
}