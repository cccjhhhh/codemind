package com.codemind.skill.routing;

import com.codemind.skill.SkillRouteDto;
import com.codemind.skill.SkillDefinition;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;
import java.util.ArrayList;
import java.util.Comparator;

/**
 * 技能路由器：支持置信度评分和回退机制。
 *
 * 路由策略：
 * 1. 关键词匹配（Tier 1）：确定性匹配，置信度 1.0
 * 2. 语义匹配（Tier 2）：基于关键词频率，置信度 0.6-0.9
 * 3. 回退机制：当置信度不足时，返回最佳匹配供用户确认
 */
public class SkillRouter {

    private static final Logger log = LoggerFactory.getLogger(SkillRouter.class);

    /** 置信度阈值：低于此值需要用户确认 */
    private static final double CONFIDENCE_THRESHOLD = 0.6;

    /** 关键词匹配最小长度 */
    private static final int MIN_KEYWORD_LENGTH = 2;

    private final KeywordSkillRouter keywordRouter;
    private final List<SkillDefinition> skills;

    public SkillRouter(List<SkillDefinition> skills) {
        this.keywordRouter = new KeywordSkillRouter(skills);
        this.skills = skills != null ? skills : List.of();
    }

    /**
     * 路由用户输入到最佳技能
     *
     * @param userInput 用户输入
     * @return 技能路由结果，如果没有匹配返回 null
     */
    public SkillRouteDto route(String userInput) {
        if (userInput == null || userInput.isBlank()) {
            return null;
        }

        // Tier 1: 关键词匹配（确定性）
        SkillRouteDto keywordResult = keywordRouter.route(userInput);
        if (keywordResult != null) {
            log.debug("技能路由: 关键词匹配成功 (技能: {}, 关键词: {})",
                keywordResult.skill().getName(), keywordResult.matchedKeyword());
            return keywordResult;
        }

        // Tier 2: 语义匹配（基于关键词频率）
        SkillRouteDto semanticResult = semanticRoute(userInput);
        if (semanticResult != null && semanticResult.confidence() >= CONFIDENCE_THRESHOLD) {
            log.debug("技能路由: 语义匹配成功 (技能: {}, 置信度: {:.2f})",
                semanticResult.skill().getName(), semanticResult.confidence());
            return semanticResult;
        }

        // Tier 3: 回退机制（返回最佳匹配供用户确认）
        if (semanticResult != null && semanticResult.confidence() > 0.3) {
            log.info("技能路由: 置信度不足，返回候选供确认 (技能: {}, 置信度: {:.2f})",
                semanticResult.skill().getName(), semanticResult.confidence());
            return semanticResult;
        }

        log.debug("技能路由: 无匹配技能");
        return null;
    }

    /**
     * 语义路由：基于关键词频率计算置信度
     */
    private SkillRouteDto semanticRoute(String userInput) {
        String lowerInput = userInput.toLowerCase();
        List<SkillScore> scores = new ArrayList<>();

        for (SkillDefinition skill : skills) {
            if (skill.matchesDisabled(lowerInput)) {
                continue;
            }

            double score = calculateSemanticScore(lowerInput, skill);
            if (score > 0) {
                scores.add(new SkillScore(skill, score));
            }
        }

        // 按置信度排序，返回最佳匹配
        return scores.stream()
            .max(Comparator.comparingDouble(s -> s.score))
            .map(s -> SkillRouteDto.semanticMatch(s.skill, "semantic match", s.score))
            .orElse(null);
    }

    /**
     * 计算语义匹配置信度
     *
     * 策略：
     * - 匹配触发关键词：+0.3 per keyword
     * - 匹配禁用关键词：-0.5 per keyword
     * - 输入长度惩罚：过长的输入降低置信度
     */
    private double calculateSemanticScore(String lowerInput, SkillDefinition skill) {
        double score = 0;

        // 匹配触发关键词
        List<String> triggerKeywords = skill.getTriggerKeywords();
        if (triggerKeywords != null) {
            for (String keyword : triggerKeywords) {
                if (keyword.length() >= MIN_KEYWORD_LENGTH && lowerInput.contains(keyword.toLowerCase())) {
                    score += 0.3;
                }
            }
        }

        // 匹配禁用关键词（降低置信度）
        List<String> disabledKeywords = skill.getDisabledKeywords();
        if (disabledKeywords != null) {
            for (String keyword : disabledKeywords) {
                if (keyword.length() >= MIN_KEYWORD_LENGTH && lowerInput.contains(keyword.toLowerCase())) {
                    score -= 0.5;
                }
            }
        }

        // 输入长度惩罚（过长的输入可能是闲聊）
        if (lowerInput.length() > 100) {
            score *= 0.8;
        }

        return Math.max(0, Math.min(score, 1.0));
    }

    /**
     * 获取所有候选技能（用于回退机制）
     */
    public List<SkillRouteDto> getCandidates(String userInput, int maxCandidates) {
        if (userInput == null || userInput.isBlank()) {
            return List.of();
        }

        String lowerInput = userInput.toLowerCase();
        List<SkillScore> scores = new ArrayList<>();

        for (SkillDefinition skill : skills) {
            if (skill.matchesDisabled(lowerInput)) {
                continue;
            }

            double score = calculateSemanticScore(lowerInput, skill);
            if (score > 0) {
                scores.add(new SkillScore(skill, score));
            }
        }

        // 按置信度排序，返回前N个候选
        return scores.stream()
            .sorted(Comparator.comparingDouble((SkillScore s) -> s.score).reversed())
            .limit(maxCandidates)
            .map(s -> SkillRouteDto.semanticMatch(s.skill, "candidate", s.score))
            .toList();
    }

    /**
     * 技能分数记录
     */
    private record SkillScore(SkillDefinition skill, double score) {}
}
