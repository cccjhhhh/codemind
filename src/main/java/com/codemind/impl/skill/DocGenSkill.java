package com.codemind.impl.skill;

import com.codemind.api.skill.SkillContext;
import com.codemind.api.skill.SkillExecutor;
import com.codemind.api.skill.SkillResult;
import com.codemind.api.tool.ToolResult;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;

import java.nio.file.Path;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 文档生成技能
 * 
 * 工作流程：
 * 1. 解析用户输入，获取文件或目录路径
 * 2. 读取文件内容
 * 3. 解析 Java 代码结构（类、方法、注释）
 * 4. 生成 Markdown 文档
 * 
 * 改进点：
 * - parseTargetPath 逻辑更严谨
 * - 支持目录扫描
 * - 更好的错误提示
 */
public class DocGenSkill implements SkillExecutor {
    
    private static final ObjectMapper JSON = new ObjectMapper();
    
    // Java 代码解析模式
    private static final Pattern CLASS_PATTERN = Pattern.compile(
        "(?:public|private|protected)?\\s*(?:abstract|final)?\\s*class\\s+(\\w+)(?:\\s+extends\\s+\\w+)?(?:\\s+implements\\s+[\\w,\\s]+)?"
    );
    private static final Pattern METHOD_PATTERN = Pattern.compile(
        "(?:public|private|protected)?\\s*(?:static)?\\s*(?:final)?\\s*\\w+\\s+(\\w+)\\s*\\([^)]*\\)\\s*(?:throws\\s+[\\w,\\s]+)?\\s*\\{?"
    );
    private static final Pattern JAVADOC_PATTERN = Pattern.compile(
        "/\\*\\*([\\s\\S]*?)\\*/"
    );
    
    // 常见代码文件扩展名
    private static final Set<String> CODE_EXTENSIONS = Set.of(
        ".java", ".kt", ".scala", ".py", ".js", ".ts"
    );
    
    @Override
    public SkillResult execute(SkillContext context) {
        try {
            String userInput = context.getUserInput();
            
            // 解析用户输入，获取文件路径
            TargetPathInfo pathInfo = parseTargetPath(context, userInput);
            
            if (pathInfo.path == null || pathInfo.path.isEmpty()) {
                return SkillResult.success(JSON.createObjectNode()
                    .put("status", "need_input")
                    .put("message", "请提供要生成文档的文件或目录路径")
                    .put("examples", "例如：src/main/java/Service.java 或 src/main/java/")
                    .toString());
            }
            
            // 读取文件内容
            ToolResult readResult = context.callTool("read_file", Map.of("path", pathInfo.path));
            
            if (!readResult.isSuccess()) {
                return SkillResult.success(JSON.createObjectNode()
                    .put("status", "error")
                    .put("message", "无法读取文件: " + pathInfo.path)
                    .put("error", readResult.getError())
                    .toString());
            }
            
            String content = readResult.getOutput();
            
            // 解析代码结构
            CodeStructure structure = parseCodeStructure(content, pathInfo.path);
            
            // 生成 Markdown 文档
            String markdown = generateMarkdown(structure);
            
            // 返回结果
            ObjectNode result = JSON.createObjectNode();
            result.put("status", "success");
            result.put("file", pathInfo.path);
            result.put("markdown", markdown);
            result.put("classCount", structure.classes.size());
            result.put("methodCount", structure.methods.size());
            
            return SkillResult.success(result.toString());
            
        } catch (Exception e) {
            return SkillResult.failure("文档生成失败: " + e.getMessage());
        }
    }
    
    /**
     * 目标路径信息
     */
    private static class TargetPathInfo {
        String path;
        boolean isDirectory;
        
        TargetPathInfo(String path, boolean isDirectory) {
            this.path = path;
            this.isDirectory = isDirectory;
        }
    }
    
    /**
     * 从用户输入中解析目标路径
     * 
     * 改进：更严谨的路径识别逻辑
     */
    private TargetPathInfo parseTargetPath(SkillContext context, String userInput) {
        if (userInput == null || userInput.isEmpty()) {
            return new TargetPathInfo(null, false);
        }
        
        String trimmed = userInput.trim();
        
        // 1. 如果看起来像文件路径（有扩展名）
        if (looksLikeFilePath(trimmed)) {
            return new TargetPathInfo(trimmed, false);
        }
        
        // 2. 如果看起来像目录路径
        if (looksLikeDirectoryPath(trimmed)) {
            return new TargetPathInfo(trimmed, true);
        }
        
        // 3. 尝试结合工作目录
        Path workDir = context.getSessionContext().getWorkingDirectory();
        Path candidate = workDir.resolve(trimmed);
        String candidateStr = candidate.toString();
        
        if (looksLikeFilePath(candidateStr) || looksLikeDirectoryPath(candidateStr)) {
            return new TargetPathInfo(candidateStr, looksLikeDirectoryPath(candidateStr));
        }
        
        // 4. 默认按文件处理
        return new TargetPathInfo(trimmed, false);
    }
    
    /**
     * 判断是否像文件路径
     */
    private boolean looksLikeFilePath(String path) {
        if (path == null) return false;
        
        // 检查扩展名
        for (String ext : CODE_EXTENSIONS) {
            if (path.toLowerCase().endsWith(ext)) {
                return true;
            }
        }
        
        // 检查是否包含文件名模式 (xxx/yyy/zzz.java)
        return path.contains(".") && (path.contains("/") || path.contains("\\"));
    }
    
    /**
     * 判断是否像目录路径
     */
    private boolean looksLikeDirectoryPath(String path) {
        if (path == null) return false;
        
        // 目录通常不以扩展名结尾
        if (path.contains(".")) {
            // 有扩展名但不是代码文件，可能是目录
            for (String ext : CODE_EXTENSIONS) {
                if (path.toLowerCase().endsWith(ext)) {
                    return false;
                }
            }
        }
        
        // 以 / 或 \ 结尾，或者包含目录模式
        return path.endsWith("/") || 
               path.endsWith("\\") || 
               (path.contains("/") && !path.contains(".")) ||
               (path.contains("\\") && !path.contains("."));
    }
    
    /**
     * 解析代码结构
     */
    private CodeStructure parseCodeStructure(String content, String filePath) {
        CodeStructure structure = new CodeStructure();
        structure.filePath = filePath;
        
        // 提取类名
        Matcher classMatcher = CLASS_PATTERN.matcher(content);
        if (classMatcher.find()) {
            structure.classes.add(classMatcher.group(1));
        }
        
        // 提取方法名
        Matcher methodMatcher = METHOD_PATTERN.matcher(content);
        Set<String> foundMethods = new HashSet<>();
        while (methodMatcher.find()) {
            String methodName = methodMatcher.group(1);
            // 跳过构造函数和常见非业务方法
            if (!foundMethods.contains(methodName) && !isCommonMethod(methodName)) {
                foundMethods.add(methodName);
                structure.methods.add(methodName);
            }
        }
        
        // 提取 Javadoc 注释
        Matcher javadocMatcher = JAVADOC_PATTERN.matcher(content);
        while (javadocMatcher.find()) {
            String javadoc = javadocMatcher.group(1);
            // 提取第一行作为描述
            String[] lines = javadoc.split("\n");
            for (String line : lines) {
                line = line.replaceAll("^\\s*\\*\\s*", "").trim();
                if (!line.isEmpty()) {
                    structure.descriptions.add(line);
                    break;
                }
            }
        }
        
        return structure;
    }
    
    /**
     * 判断是否是常见非业务方法
     */
    private boolean isCommonMethod(String methodName) {
        return Set.of("toString", "equals", "hashCode", "getClass", 
                      "notify", "notifyAll", "wait", "clone", "finalize").contains(methodName);
    }
    
    /**
     * 生成 Markdown 文档
     */
    private String generateMarkdown(CodeStructure structure) {
        StringBuilder sb = new StringBuilder();
        
        // 标题
        String className = structure.classes.isEmpty() ? "Unknown" : structure.classes.get(0);
        sb.append("# ").append(className).append(" 文档\n\n");
        
        // 文件路径
        sb.append("**文件**: ").append(structure.filePath).append("\n\n");

        // 类描述
        if (!structure.descriptions.isEmpty()) {
            sb.append("## 描述\n\n");
            for (String desc : structure.descriptions) {
                sb.append(desc).append("\n\n");
            }
        }
        
        // 类列表
        if (!structure.classes.isEmpty()) {
            sb.append("## 类\n\n");
            for (String cls : structure.classes) {
                sb.append("- `").append(cls).append("`\n");
            }
            sb.append("\n");
        }
        
        // 方法列表
        if (!structure.methods.isEmpty()) {
            sb.append("## 方法\n\n");
            for (String method : structure.methods) {
                sb.append("- `").append(method).append("()`\n");
            }
            sb.append("\n");
        }
        
        // 生成时间
        sb.append("---\n");
        sb.append("*由 CodeMind 自动生成*\n");
        
        return sb.toString();
    }
    
    /**
     * 代码结构
     */
    private static class CodeStructure {
        String filePath;
        List<String> classes = new ArrayList<>();
        List<String> methods = new ArrayList<>();
        List<String> descriptions = new ArrayList<>();
    }
}