package com.codemind.impl.cli;

import com.codemind.api.cli.OutputFormatter;
import com.codemind.api.safety.Permission;
import com.codemind.api.safety.PermissionDecision;
import com.codemind.api.safety.PermissionPrompter;

import java.util.Scanner;

/**
 * CLI 权限询问器
 * 
 * 实现权限确认的用户交互。
 * 
 * 设计原则：
 * - 单一职责原则（SRP）：只负责权限确认的用户交互
 * - 接口分离原则（ISP）：实现 PermissionPrompter 接口
 * 
 * 注意：此类从 CLI 内部类提取为独立类，
 *       符合"内部类不应实现接口"的架构约束。
 */
public class CLIPermissionPrompter implements PermissionPrompter {
    
    private final OutputFormatter outputFormatter;
    
    public CLIPermissionPrompter(OutputFormatter outputFormatter) {
        this.outputFormatter = outputFormatter;
    }
    
    public CLIPermissionPrompter() {
        this.outputFormatter = new DefaultOutputFormatter();
    }
    
    @Override
    public PermissionDecision prompt(Permission permission, String context) {
        System.out.print(outputFormatter.formatPermissionPrompt(permission, context));
        
        Scanner scanner = new Scanner(System.in);
        String input = scanner.nextLine().trim().toLowerCase();
        
        switch (input) {
            case "y":
            case "yes":
            case "1":
                return PermissionDecision.ALLOW;
            case "s":
            case "session":
            case "2":
                return PermissionDecision.ALLOW_SESSION;
            case "n":
            case "no":
            case "3":
            default:
                return PermissionDecision.DENY;
        }
    }
}