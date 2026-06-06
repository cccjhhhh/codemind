package com.codemind.impl.builtin.skill;

import com.codemind.api.analysis.DependencyGraph;
import com.codemind.api.skill.SkillContext;
import com.codemind.api.skill.SkillExecutor;
import com.codemind.api.skill.SkillResult;
import com.codemind.api.tool.ToolResult;
import com.codemind.impl.analysis.DependencyGraphImpl;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;

import java.nio.file.Path;
import java.util.*;

public class CodeReviewSkill implements SkillExecutor {
    
    private static final ObjectMapper JSON = new ObjectMapper();
    private final DependencyGraph dependencyGraph;
    private static final int DEFAULT_MAX_HOPS = 2;
    private static final double HIGH_RISK_THRESHOLD = 0.5;
    
    public CodeReviewSkill() {
        this(new DependencyGraphImpl());
    }
    
    public CodeReviewSkill(DependencyGraph dependencyGraph) {
        this.dependencyGraph = Objects.requireNonNull(dependencyGraph, "dependencyGraph cannot be null");
    }
    
    @Override
    public SkillResult execute(SkillContext context) {
        try {
            if (!isGitRepository(context)) {
                Path workDir = context.getSessionContext().getWorkingDirectory();
                return SkillResult.success(JSON.createObjectNode()
                    .put("status", "not_git_repo")
                    .put("message", "当前目录不是 git 仓库，无法获取代码变更")
                    .put("workingDirectory", workDir.toString())
                    .put("suggestion", "请告诉用户：当前工作目录不是 git 仓库。如果要审查代码，需要先执行 git init 或切换到 git 仓库目录。")
                    .put("action", "reply_to_user")
                    .toString());
            }
            
            String diff = getGitDiff(context);
            
            if (diff == null || diff.isEmpty()) {
                return SkillResult.success(JSON.createObjectNode()
                    .put("status", "no_changes")
                    .put("message", "没有检测到代码变更")
                    .put("hint", "请先用 git add 添加变更，或修改代码文件")
                    .toString());
            }
            
            List<String> changedFiles = parseChangedFiles(diff);
            
            if (changedFiles.isEmpty()) {
                return SkillResult.success(JSON.createObjectNode()
                    .put("status", "no_files")
                    .put("message", "无法解析变更文件")
                    .put("diffPreview", truncateDiff(diff, 500))
                    .toString());
            }
            
            Path workingDir = context.getSessionContext().getWorkingDirectory();
            
            try {
                dependencyGraph.build(workingDir);
            } catch (Exception e) {
                logDependencyGraphError(e);
            }
            
            Set<String> changedSet = new HashSet<>(changedFiles);
            Set<String> affectedFiles = new HashSet<>();
            
            try {
                affectedFiles = dependencyGraph.findAffectedFiles(changedSet, DEFAULT_MAX_HOPS);
            } catch (Exception e) {
                affectedFiles = changedSet;
            }
            
            Set<String> allFilesToAnalyze = new HashSet<>(affectedFiles);
            Map<String, String> fileContents = readFiles(context, allFilesToAnalyze);
            Map<String, Double> riskScores = calculateRiskScores(changedFiles, dependencyGraph);
            
            ObjectNode result = JSON.createObjectNode();
            result.put("status", "success");
            
            try {
                result.put("dependencyGraphStats", dependencyGraph.getStats());
            } catch (Exception e) {
                result.put("dependencyGraphStats", "构建失败");
            }
            
            ObjectNode changes = result.putObject("changes");
            changes.put("count", changedFiles.size());
            changes.put("maxHops", DEFAULT_MAX_HOPS);
            
            ArrayNode changedFilesArray = changes.putArray("files");
            for (String file : changedFiles) {
                ObjectNode fileNode = changedFilesArray.addObject();
                fileNode.put("path", file);
                fileNode.put("riskScore", riskScores.getOrDefault(file, 0.0));
                fileNode.put("isHighRisk", riskScores.getOrDefault(file, 0.0) >= HIGH_RISK_THRESHOLD);
            }
            
            ObjectNode affected = result.putObject("affected");
            affected.put("count", affectedFiles.size());
            
            ArrayNode affectedFilesArray = affected.putArray("files");
            for (String file : affectedFiles) {
                if (!changedSet.contains(file)) {
                    ObjectNode fileNode = affectedFilesArray.addObject();
                    fileNode.put("path", file);
                    fileNode.put("riskScore", riskScores.getOrDefault(file, 0.0));
                }
            }
            
            ObjectNode contents = result.putObject("contents");
            List<String> prioritizedFiles = new ArrayList<>();
            prioritizedFiles.addAll(changedFiles);
            prioritizedFiles.addAll(affectedFiles);
            
            for (String file : prioritizedFiles) {
                if (contents.has(file)) continue;
                
                if (fileContents.containsKey(file)) {
                    ObjectNode fileNode = contents.putObject(file);
                    fileNode.put("content", truncateContent(fileContents.get(file), 300));
                    fileNode.put("isChanged", changedSet.contains(file));
                    fileNode.put("isAffected", !changedSet.contains(file) && affectedFiles.contains(file));
                }
            }
            
            ArrayNode highRiskArray = result.putArray("highRiskFiles");
            riskScores.entrySet().stream()
                .filter(e -> e.getValue() >= HIGH_RISK_THRESHOLD)
                .sorted((a, b) -> Double.compare(b.getValue(), a.getValue()))
                .forEach(e -> {
                    ObjectNode node = highRiskArray.addObject();
                    node.put("path", e.getKey());
                    node.put("riskScore", e.getValue());
                });
            
            ObjectNode stats = result.putObject("stats");
            stats.put("totalFilesToAnalyze", allFilesToAnalyze.size());
            stats.put("changedFiles", changedFiles.size());
            stats.put("affectedFiles", affectedFiles.size() - changedFiles.size());
            stats.put("highRiskCount", highRiskArray.size());
            
            result.put("diffPreview", truncateDiff(diff, 2000));
            
            return SkillResult.success(result.toString());
            
        } catch (Exception e) {
            return SkillResult.failure("代码审查失败: " + e.getMessage());
        }
    }
    
    private boolean isGitRepository(SkillContext context) {
        Path workDir = context.getSessionContext().getWorkingDirectory();
        ToolResult result = context.callTool("execute_command", 
            Map.of("command", "git rev-parse --is-inside-work-tree", "timeout", 5, "cwd", workDir.toString()));
        return result.isSuccess() && 
               result.getOutput() != null && 
               result.getOutput().trim().equals("true");
    }
    
    private String getGitDiff(SkillContext context) {
        Path workDir = context.getSessionContext().getWorkingDirectory();
        
        ToolResult stagedResult = context.callTool("execute_command", 
            Map.of("command", "git diff --cached", "timeout", 30, "cwd", workDir.toString()));
        
        if (stagedResult.isSuccess() && 
            stagedResult.getOutput() != null && 
            !stagedResult.getOutput().isEmpty()) {
            return stagedResult.getOutput();
        }
        
        ToolResult unstagedResult = context.callTool("execute_command", 
            Map.of("command", "git diff", "timeout", 30, "cwd", workDir.toString()));
        
        if (unstagedResult.isSuccess()) {
            return unstagedResult.getOutput();
        }
        
        return "";
    }
    
    private List<String> parseChangedFiles(String diff) {
        List<String> files = new ArrayList<>();
        java.util.regex.Pattern pattern = 
            java.util.regex.Pattern.compile("^diff --git a/(.+?) b/.+$", 
                java.util.regex.Pattern.MULTILINE);
        java.util.regex.Matcher matcher = pattern.matcher(diff);
        
        while (matcher.find()) {
            files.add(matcher.group(1));
        }
        
        return files;
    }
    
    private Map<String, String> readFiles(SkillContext context, Set<String> files) {
        Map<String, String> contents = new HashMap<>();
        Path workingDir = context.getSessionContext().getWorkingDirectory();
        
        for (String file : files) {
            try {
                Path filePath = workingDir.resolve(file);
                ToolResult result = context.callTool("read_file", 
                    Map.of("path", filePath.toString()));
                
                if (result.isSuccess() && result.getOutput() != null) {
                    contents.put(file, result.getOutput());
                }
            } catch (Exception e) {
            }
        }
        
        return contents;
    }
    
    private Map<String, Double> calculateRiskScores(List<String> files, DependencyGraph graph) {
        Map<String, Double> scores = new HashMap<>();
        for (String file : files) {
            try {
                scores.put(file, graph.calculateRiskScore(file));
            } catch (Exception e) {
                scores.put(file, 0.3);
            }
        }
        return scores;
    }
    
    private void logDependencyGraphError(Exception e) {
    }
    
    private String truncateDiff(String diff, int maxLength) {
        if (diff == null) return "";
        if (diff.length() <= maxLength) {
            return diff;
        }
        return diff.substring(0, maxLength) + "\n\n... (diff 过长已截断)";
    }
    
    private String truncateContent(String content, int maxLines) {
        if (content == null) return "";
        String[] lines = content.split("\n");
        if (lines.length <= maxLines) {
            return content;
        }
        
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < maxLines; i++) {
            sb.append(lines[i]).append("\n");
        }
        sb.append("\n... (").append(lines.length - maxLines).append(" more lines)");
        return sb.toString();
    }
}
