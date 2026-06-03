package com.codemind.impl.skill;

import com.codemind.api.analysis.DependencyGraph;
import com.codemind.api.skill.Skill;
import com.codemind.api.skill.SkillContext;
import com.codemind.api.skill.SkillResult;
import com.codemind.api.tool.ToolResult;
import com.codemind.impl.analysis.DependencyGraphImpl;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;

import java.nio.file.Path;
import java.util.*;

/**
 * 代码审查技能（第三版 - SkillAsTool 架构）
 * 
 * 工作流程：
 * 1. 获取 git diff（暂存区优先，回退到工作区）
 * 2. 解析变更文件列表
 * 3. 构建依赖图（解析 import 语句）
 * 4. 查找受影响文件（BFS 遍历）
 * 5. 读取文件内容（变更文件 + 受影响文件）
 * 6. 计算风险评分
 * 7. 返回结构化数据（供 LLM 深度分析）
 * 
 * 架构变化：
 * - 通过 SkillContext.callTool() 调用其他 Tool，而非直接持有 ToolRegistry
 * - SkillAsTool 负责将 Skill 包装成 Tool 暴露给 LLM
 * 
 * 学习要点：
 * - 依赖图构建（import 解析）
 * - BFS 遍历算法
 * - 影响范围分析
 * - 风险评分计算
 * - Skill 与 Tool 的协作模式
 */
public class CodeReviewSkill implements Skill {
    
    private static final ObjectMapper JSON = new ObjectMapper();
    
    // 默认 BFS 最大跳数
    private static final int DEFAULT_MAX_HOPS = 2;
    
    // 默认风险评分阈值
    private static final double HIGH_RISK_THRESHOLD = 0.5;
    
    private final DependencyGraph dependencyGraph;
    
    public CodeReviewSkill() {
        this.dependencyGraph = new DependencyGraphImpl();
    }
    
    @Override
    public String getName() {
        return "code_review";
    }
    
    @Override
    public String getDescription() {
        return "对代码变更进行审查，发现潜在问题并提供改进建议。\n" +
               "支持依赖图分析，找出变更可能影响的其他文件。";
    }
    
    @Override
    public SkillResult execute(SkillContext context) {
        try {
            // =============================================
            // 步骤 1: 获取 git diff
            // =============================================
            String diff = getGitDiff(context);
            
            if (diff == null || diff.isEmpty()) {
                return SkillResult.success(JSON.createObjectNode()
                    .put("status", "no_changes")
                    .put("message", "没有检测到代码变更")
                    .toString());
            }
            
            // =============================================
            // 步骤 2: 解析变更文件列表
            // =============================================
            List<String> changedFiles = parseChangedFiles(diff);
            
            if (changedFiles.isEmpty()) {
                return SkillResult.success(JSON.createObjectNode()
                    .put("status", "no_files")
                    .put("message", "无法解析变更文件")
                    .put("diffPreview", truncateDiff(diff, 500))
                    .toString());
            }
            
            // =============================================
            // 步骤 3: 构建依赖图
            // =============================================
            Path workingDir = context.getSessionContext().getWorkingDirectory();
            dependencyGraph.build(workingDir);
            
            // =============================================
            // 步骤 4: 查找受影响文件
            // =============================================
            Set<String> changedSet = new HashSet<>(changedFiles);
            Set<String> affectedFiles = dependencyGraph.findAffectedFiles(changedSet, DEFAULT_MAX_HOPS);
            
            // 合并变更文件和受影响文件（去重）
            Set<String> allFilesToAnalyze = new HashSet<>(affectedFiles);
            
            // =============================================
            // 步骤 5: 读取文件内容
            // =============================================
            Map<String, String> fileContents = readFiles(context, allFilesToAnalyze);
            
            // =============================================
            // 步骤 6: 计算风险评分
            // =============================================
            Map<String, Double> riskScores = calculateRiskScores(changedFiles);
            
            // =============================================
            // 步骤 7: 构建结构化结果
            // =============================================
            ObjectNode result = JSON.createObjectNode();
            result.put("status", "success");
            result.put("dependencyGraphStats", dependencyGraph.getStats());
            
            // 变更文件信息
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
            
            // 受影响文件信息
            ObjectNode affected = result.putObject("affected");
            affected.put("count", affectedFiles.size());
            
            ArrayNode affectedFilesArray = affected.putArray("files");
            for (String file : affectedFiles) {
                if (!changedSet.contains(file)) { // 不重复包含变更文件
                    ObjectNode fileNode = affectedFilesArray.addObject();
                    fileNode.put("path", file);
                    fileNode.put("riskScore", riskScores.getOrDefault(file, 0.0));
                }
            }
            
            // 文件内容（变更文件优先）
            ObjectNode contents = result.putObject("contents");
            List<String> prioritizedFiles = new ArrayList<>();
            prioritizedFiles.addAll(changedFiles); // 变更文件在前
            prioritizedFiles.addAll(affectedFiles); // 受影响文件在后
            
            for (String file : prioritizedFiles) {
                if (contents.has(file)) continue; // 避免重复
                
                if (fileContents.containsKey(file)) {
                    ObjectNode fileNode = contents.putObject(file);
                    fileNode.put("content", truncateContent(fileContents.get(file), 300));
                    fileNode.put("isChanged", changedSet.contains(file));
                    fileNode.put("isAffected", !changedSet.contains(file) && affectedFiles.contains(file));
                }
            }
            
            // 高风险文件列表（供 Agent 重点关注）
            ArrayNode highRiskArray = result.putArray("highRiskFiles");
            riskScores.entrySet().stream()
                .filter(e -> e.getValue() >= HIGH_RISK_THRESHOLD)
                .sorted((a, b) -> Double.compare(b.getValue(), a.getValue()))
                .forEach(e -> {
                    ObjectNode node = highRiskArray.addObject();
                    node.put("path", e.getKey());
                    node.put("riskScore", e.getValue());
                });
            
            // 统计信息
            ObjectNode stats = result.putObject("stats");
            stats.put("totalFilesToAnalyze", allFilesToAnalyze.size());
            stats.put("changedFiles", changedFiles.size());
            stats.put("affectedFiles", affectedFiles.size() - changedFiles.size());
            stats.put("highRiskCount", highRiskArray.size());
            
            // diff 预览
            result.put("diffPreview", truncateDiff(diff, 2000));
            
            return SkillResult.success(result.toString());
            
        } catch (Exception e) {
            return SkillResult.failure("代码审查失败: " + e.getMessage());
        }
    }
    
    /**
     * 获取 git diff
     */
    private String getGitDiff(SkillContext context) {
        // 先尝试暂存区
        ToolResult stagedResult = context.callTool("execute_command", 
            Map.of("command", "git diff --cached", "timeout", 30));
        
        if (stagedResult.isSuccess() && 
            stagedResult.getOutput() != null && 
            !stagedResult.getOutput().isEmpty()) {
            return stagedResult.getOutput();
        }
        
        // 回退到工作区
        ToolResult unstagedResult = context.callTool("execute_command", 
            Map.of("command", "git diff", "timeout", 30));
        
        if (unstagedResult.isSuccess()) {
            return unstagedResult.getOutput();
        }
        
        return "";
    }
    
    /**
     * 解析 git diff 输出，提取变更文件列表
     */
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
    
    /**
     * 读取文件内容
     */
    private Map<String, String> readFiles(SkillContext context, Set<String> files) {
        Map<String, String> contents = new HashMap<>();
        
        for (String file : files) {
            try {
                ToolResult result = context.callTool("read_file", 
                    Map.of("path", file));
                
                if (result.isSuccess() && result.getOutput() != null) {
                    contents.put(file, result.getOutput());
                }
            } catch (Exception e) {
                // 单个文件失败不影响整体
            }
        }
        
        return contents;
    }
    
    /**
     * 计算风险评分
     */
    private Map<String, Double> calculateRiskScores(List<String> files) {
        Map<String, Double> scores = new HashMap<>();
        for (String file : files) {
            scores.put(file, dependencyGraph.calculateRiskScore(file));
        }
        return scores;
    }
    
    /**
     * 截断 diff 预览
     */
    private String truncateDiff(String diff, int maxLength) {
        if (diff == null) return "";
        if (diff.length() <= maxLength) {
            return diff;
        }
        return diff.substring(0, maxLength) + "\n\n... (diff 过长已截断)";
    }
    
    /**
     * 截断文件内容预览
     */
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
