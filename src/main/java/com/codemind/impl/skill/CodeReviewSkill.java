package com.codemind.impl.skill;

import com.codemind.api.skill.Skill;
import com.codemind.api.skill.SkillContext;
import com.codemind.api.skill.SkillResult;
import com.codemind.api.tool.ToolRegistry;

/**
 * 代码审查技能
 * 
 * 工作流程：
 * 1. 获取待审查的代码变更
 * 2. 分析代码结构
 * 3. 检查编码规范
 * 4. 发现潜在问题
 * 5. 生成审查报告
 */
public class CodeReviewSkill implements Skill {
    
    private final ToolRegistry toolRegistry;
    
    public CodeReviewSkill(ToolRegistry toolRegistry) {
        this.toolRegistry = toolRegistry;
    }
    
    @Override
    public String getName() {
        return "code_review";
    }
    
    @Override
    public String getDescription() {
        return "对代码进行审查，发现潜在问题并提供改进建议";
    }
    
    @Override
    public SkillResult execute(SkillContext context) {
        try {
            String userInput = context.getUserInput();
            
            // 1. 获取代码变更
            var gitDiff = toolRegistry.execute("execute_command", 
                java.util.Map.of("command", "git diff"));
            
            if (!gitDiff.isSuccess()) {
                return SkillResult.failure("获取代码变更失败: " + gitDiff.getError());
            }
            
            String diff = gitDiff.getOutput();
            if (diff == null || diff.isEmpty()) {
                return SkillResult.success("没有检测到代码变更");
            }
            
            // 2. 生成审查报告
            // TODO: 调用 LLM 进行深入分析
            String report = generateReviewReport(diff);
            
            return SkillResult.success(report);
            
        } catch (Exception e) {
            return SkillResult.failure("代码审查失败: " + e.getMessage());
        }
    }
    
    private String generateReviewReport(String diff) {
        // TODO: 调用 LLM 分析代码
        // 目前是基础版本，只做简单的统计
        
        StringBuilder report = new StringBuilder();
        report.append("=== 代码审查报告 ===\n\n");
        report.append("待实现: 调用 LLM 进行深度分析\n\n");
        report.append("检测到的变更:\n");
        report.append(diff);
        return report.toString();
    }
}