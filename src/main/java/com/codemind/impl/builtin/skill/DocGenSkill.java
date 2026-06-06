package com.codemind.impl.builtin.skill;

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

public class DocGenSkill implements SkillExecutor {
    
    private static final ObjectMapper JSON = new ObjectMapper();
    
    private static final Pattern CLASS_PATTERN = Pattern.compile(
        "(?:public|private|protected)?\\s*(?:abstract|final)?\\s*class\\s+(\\w+)(?:\\s+extends\\s+\\w+)?(?:\\s+implements\\s+[\\w,\\s]+)?"
    );
    private static final Pattern METHOD_PATTERN = Pattern.compile(
        "(?:public|private|protected)?\\s*(?:static)?\\s*(?:final)?\\s*\\w+\\s+(\\w+)\\s*\\([^)]*\\)\\s*(?:throws\\s+[\\w,\\s]+)?\\s*\\{?"
    );
    private static final Pattern JAVADOC_PATTERN = Pattern.compile(
        "/\\*\\*([\\s\\S]*?)\\*/"
    );
    
    private static final Set<String> CODE_EXTENSIONS = Set.of(
        ".java", ".kt", ".scala", ".py", ".js", ".ts"
    );
    
    @Override
    public SkillResult execute(SkillContext context) {
        try {
            String userInput = context.getUserInput();
            TargetPathInfo pathInfo = parseTargetPath(context, userInput);
            
            if (pathInfo.path == null || pathInfo.path.isEmpty()) {
                if (isGenerateContentIntent(userInput)) {
                    return SkillResult.success(JSON.createObjectNode()
                        .put("action", "reply_to_user")
                        .put("message", "好的，我来帮您生成文档内容。")
                        .put("hint", "请根据用户的描述生成相应的内容，格式美观")
                        .toString());
                }
                
                return SkillResult.success(JSON.createObjectNode()
                    .put("status", "need_input")
                    .put("message", "请提供要生成文档的文件或目录路径")
                    .put("examples", "例如：src/main/java/Service.java 或 src/main/java/")
                    .toString());
            }
            
            ToolResult readResult = context.callTool("read_file", Map.of("path", pathInfo.path));
            
            if (!readResult.isSuccess()) {
                return SkillResult.success(JSON.createObjectNode()
                    .put("status", "error")
                    .put("message", "无法读取文件: " + pathInfo.path)
                    .put("error", readResult.getError())
                    .toString());
            }
            
            String content = readResult.getOutput();
            CodeStructure structure = parseCodeStructure(content, pathInfo.path);
            String markdown = generateMarkdown(structure);
            
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
    
    private boolean isGenerateContentIntent(String input) {
        if (input == null) return false;
        String lower = input.toLowerCase();
        
        return lower.contains("写一首") || 
               lower.contains("写一个") && (lower.contains("诗歌") || lower.contains("诗") || lower.contains("故事")) ||
               lower.contains("生成") && (lower.contains("诗歌") || lower.contains("故事") || lower.contains("文章")) ||
               lower.contains("创作") ||
               lower.contains("帮我写");
    }
    
    private static class TargetPathInfo {
        String path;
        boolean isDirectory;
        
        TargetPathInfo(String path, boolean isDirectory) {
            this.path = path;
            this.isDirectory = isDirectory;
        }
    }
    
    private TargetPathInfo parseTargetPath(SkillContext context, String userInput) {
        if (userInput == null || userInput.isEmpty()) {
            return new TargetPathInfo(null, false);
        }
        
        String trimmed = userInput.trim();
        
        if (isNaturalLanguageSentence(trimmed)) {
            return new TargetPathInfo(null, false);
        }
        
        if (looksLikeFilePath(trimmed)) {
            return new TargetPathInfo(trimmed, false);
        }
        
        if (looksLikeDirectoryPath(trimmed)) {
            return new TargetPathInfo(trimmed, true);
        }
        
        Path workDir = context.getSessionContext().getWorkingDirectory();
        Path candidate = workDir.resolve(trimmed);
        String candidateStr = candidate.toString();
        
        if (looksLikeFilePath(candidateStr) || looksLikeDirectoryPath(candidateStr)) {
            return new TargetPathInfo(candidateStr, looksLikeDirectoryPath(candidateStr));
        }
        
        return new TargetPathInfo(null, false);
    }
    
    private boolean isNaturalLanguageSentence(String input) {
        if (input == null || input.isEmpty()) return false;
        
        if (input.matches(".*[，。！？、；：\u201c\u201d''（）【】].*")) {
            return true;
        }
        
        long chineseCharCount = input.chars().filter(c -> c >= 0x4E00 && c <= 0x9FFF).count();
        if (chineseCharCount > 4) {
            if (!input.contains("/") && !input.contains("\\") && !input.contains(".")) {
                return true;
            }
        }
        
        String lower = input.toLowerCase();
        if (lower.contains(" with ") || lower.contains(" about ") || 
            lower.contains(" for ") || lower.contains(" that ") ||
            lower.contains(" please ") || lower.contains(" write ")) {
            return true;
        }
        
        return false;
    }
    
    private boolean looksLikeFilePath(String path) {
        if (path == null) return false;
        
        for (String ext : CODE_EXTENSIONS) {
            if (path.toLowerCase().endsWith(ext)) {
                return true;
            }
        }
        
        return path.contains(".") && (path.contains("/") || path.contains("\\"));
    }
    
    private boolean looksLikeDirectoryPath(String path) {
        if (path == null) return false;
        
        if (path.contains(".")) {
            for (String ext : CODE_EXTENSIONS) {
                if (path.toLowerCase().endsWith(ext)) {
                    return false;
                }
            }
        }
        
        return path.endsWith("/") || 
               path.endsWith("\\") || 
               (path.contains("/") && !path.contains(".")) ||
               (path.contains("\\") && !path.contains("."));
    }
    
    private CodeStructure parseCodeStructure(String content, String filePath) {
        CodeStructure structure = new CodeStructure();
        structure.filePath = filePath;
        
        Matcher classMatcher = CLASS_PATTERN.matcher(content);
        if (classMatcher.find()) {
            structure.classes.add(classMatcher.group(1));
        }
        
        Matcher methodMatcher = METHOD_PATTERN.matcher(content);
        Set<String> foundMethods = new HashSet<>();
        while (methodMatcher.find()) {
            String methodName = methodMatcher.group(1);
            if (!foundMethods.contains(methodName) && !isCommonMethod(methodName)) {
                foundMethods.add(methodName);
                structure.methods.add(methodName);
            }
        }
        
        Matcher javadocMatcher = JAVADOC_PATTERN.matcher(content);
        while (javadocMatcher.find()) {
            String javadoc = javadocMatcher.group(1);
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
    
    private boolean isCommonMethod(String methodName) {
        return Set.of("toString", "equals", "hashCode", "getClass", 
                      "notify", "notifyAll", "wait", "clone", "finalize").contains(methodName);
    }
    
    private String generateMarkdown(CodeStructure structure) {
        StringBuilder sb = new StringBuilder();
        
        String className = structure.classes.isEmpty() ? "Unknown" : structure.classes.get(0);
        sb.append("# ").append(className).append(" 文档\n\n");
        
        sb.append("**文件**: ").append(structure.filePath).append("\n\n");

        if (!structure.descriptions.isEmpty()) {
            sb.append("## 描述\n\n");
            for (String desc : structure.descriptions) {
                sb.append(desc).append("\n\n");
            }
        }
        
        if (!structure.classes.isEmpty()) {
            sb.append("## 类\n\n");
            for (String cls : structure.classes) {
                sb.append("- `").append(cls).append("`\n");
            }
            sb.append("\n");
        }
        
        if (!structure.methods.isEmpty()) {
            sb.append("## 方法\n\n");
            for (String method : structure.methods) {
                sb.append("- `").append(method).append("()`\n");
            }
            sb.append("\n");
        }
        
        sb.append("---\n");
        sb.append("*由 CodeMind 自动生成*\n");
        
        return sb.toString();
    }
    
    private static class CodeStructure {
        String filePath;
        List<String> classes = new ArrayList<>();
        List<String> methods = new ArrayList<>();
        List<String> descriptions = new ArrayList<>();
    }
}
