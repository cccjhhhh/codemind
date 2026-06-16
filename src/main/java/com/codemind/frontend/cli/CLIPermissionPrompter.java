package com.codemind.frontend.cli;

import com.codemind.frontend.output.spi.OutputFormatter;
import com.codemind.safety.spi.PermissionPrompter;
import com.codemind.frontend.cli.DefaultOutputFormatter;

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
public class CLIPermissionPrompter implements PermissionPrompter, AutoCloseable {

    private final OutputFormatter outputFormatter;
    private final Scanner scanner;

    public CLIPermissionPrompter(OutputFormatter outputFormatter) {
        this.outputFormatter = outputFormatter;
        this.scanner = new Scanner(System.in);
    }

    public CLIPermissionPrompter() {
        this.outputFormatter = new DefaultOutputFormatter();
        this.scanner = new Scanner(System.in);
    }

    @Override
    public Decision prompt(String toolName, String context) {
        System.out.print(outputFormatter.formatPermissionPrompt(toolName, context));

        String input = scanner.nextLine().trim().toLowerCase();

        switch (input) {
            case "y":
            case "yes":
            case "1":
                return Decision.ALLOW;
            case "s":
            case "session":
            case "2":
                return Decision.ALLOW_SESSION;
            case "n":
            case "no":
            case "3":
            default:
                return Decision.DENY;
        }
    }

    @Override
    public void close() {
        scanner.close();
    }
}
