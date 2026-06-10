package com.codemind.impl.skill.routing;

import com.codemind.dto.skill.SkillRouteDto;
import com.codemind.impl.skill.SkillDefinition;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;

public class ConfidenceSkillRouter extends SkillRouter {

    private static final Logger log = LoggerFactory.getLogger(ConfidenceSkillRouter.class);
    private static final double AUTO_ACTIVATE_THRESHOLD = 0.7;
    private static final double KEYWORD_FALLBACK_THRESHOLD = 0.3;

    private final KeywordSkillRouter keywordRouter;
    private final List<SkillDefinition> skills;

    public ConfidenceSkillRouter(List<SkillDefinition> skills) {
        super(skills);
        this.keywordRouter = new KeywordSkillRouter(skills);
        this.skills = skills;
    }

    @Override
    public SkillRouteDto route(String userInput) {
        if (userInput == null || userInput.length() < 5) {
            logRoute(userInput, 0.0, "Skip", null);
            return null;
        }

        String lower = userInput.toLowerCase();

        // 置信度打分
        double confidence = scoreConfidence(lower);
        String matchedSkill = findBestMatch(lower);

        if (confidence >= AUTO_ACTIVATE_THRESHOLD && matchedSkill != null) {
            SkillDefinition skill = findSkill(matchedSkill);
            if (skill != null) {
                logRoute(userInput, confidence, "Hit", matchedSkill);
                return SkillRouteDto.semanticMatch(skill, "Confidence: " + confidence, confidence);
            }
        }

        // KeywordRouter 兜底
        if (confidence >= KEYWORD_FALLBACK_THRESHOLD) {
            SkillRouteDto keywordResult = keywordRouter.route(userInput);
            if (keywordResult != null && keywordResult.shouldExecute()) {
                logRoute(userInput, confidence, "Keyword", keywordResult.skill().getName());
                return keywordResult;
            }
        }

        logRoute(userInput, confidence, "Miss", null);
        return null;
    }

    private double scoreConfidence(String lower) {
        double score = 0.0;

        // 关键词匹配加分
        for (SkillDefinition skill : skills) {
            for (String kw : skill.getTriggerKeywords()) {
                if (lower.contains(kw.toLowerCase())) {
                    score += 0.5;
                    break;
                }
            }
            if (score >= 0.5) break;
        }

        // 多关键词额外加分
        long matchCount = skills.stream()
            .flatMap(s -> s.getTriggerKeywords().stream())
            .filter(kw -> lower.contains(kw.toLowerCase()))
            .distinct()
            .count();
        if (matchCount >= 2) score += 0.3;

        // 短输入扣分
        if (lower.length() < 10) score -= 0.2;

        return Math.max(0.0, Math.min(1.0, score));
    }

    private String findBestMatch(String lower) {
        String best = null;
        int bestCount = 0;

        for (SkillDefinition skill : skills) {
            int count = 0;
            for (String kw : skill.getTriggerKeywords()) {
                if (lower.contains(kw.toLowerCase())) count++;
            }
            if (count > bestCount) {
                bestCount = count;
                best = skill.getName();
            }
        }

        return best;
    }

    private SkillDefinition findSkill(String name) {
        for (SkillDefinition s : skills) {
            if (s.getName().equals(name)) return s;
        }
        return null;
    }

    private void logRoute(String input, double confidence, String route, String skill) {
        try {
            Path logDir = Path.of(".codemind", "metrics");
            Files.createDirectories(logDir);
            String line = String.format(
                "{\"input\":\"%s\",\"confidence\":%.1f,\"route\":\"%s\",\"matchedSkill\":%s,\"timestamp\":\"%s\"}\n",
                input != null ? input.replace("\"", "\\\"") : "null",
                confidence,
                route,
                skill != null ? "\"" + skill + "\"" : "null",
                LocalDateTime.now().format(DateTimeFormatter.ISO_LOCAL_DATE_TIME)
            );
            Files.writeString(logDir.resolve("skill_routing.log"), line,
                StandardOpenOption.CREATE, StandardOpenOption.APPEND);
        } catch (IOException e) {
            log.warn("记录路由日志失败: {}", e.getMessage());
        }
    }
}
