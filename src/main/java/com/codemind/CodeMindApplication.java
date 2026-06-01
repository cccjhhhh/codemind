package com.codemind;

import com.codemind.cli.CLI;

/**
 * CodeMind - 智能编程助手
 * 
 * 基于 LLM 的智能编程助手，提供代码审查、文档生成、日志分析等功能。
 */
public class CodeMindApplication {
    
    public static void main(String[] args) {
        CLI cli = new CLI();
        int exitCode = cli.execute(args);
        System.exit(exitCode);
    }
}
