package com.codemind.impl.skill.routing;

import com.codemind.api.llm.LLMClient;
import com.codemind.api.llm.Message;
import com.codemind.api.llm.StreamEvent;
import com.codemind.dto.skill.SkillRouteDto;
import com.codemind.impl.skill.SkillDefinition;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicReference;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 语义 Skill 路由器（基于 LLM 判断用户意图 - Tier 3）
 */
public class SemanticSkillRouter {
    
    private static final Logger log = LoggerFactory.getLogger(SemanticSkillRouter.class);
    private static final double CONFIDENCE_THRESHOLD = SkillRouteDto.CONFIDENCE_THRESHOLD;
    
    private final LLMClient llmClient;
    private final KeywordSkillRouter keywordRouter;
    
    public SemanticSkillRouter(LLMClient llmClient, List<SkillDefinition> skills) {
        this.llmClient = llmClient;
        this.keywordRouter = new KeywordSkillRouter(skills);
    }
    
    public SkillRouteDto route(String userInput) {
        if (userInput == null || userInput.isBlank()) {
            return null;
        }
        
        if (containsNegativeIndicator(userInput.toLowerCase())) {
            log.debug("Negative indicator detected, skipping semantic routing");
            return null;
        }
        
        try {
            log.debug("Attempting semantic routing for: {}", userInput);
            SkillRouteDto result = routeSemantically(userInput);
            if (result != null && result.shouldExecute()) {
                log.debug("Semantic router matched: {} via '{}' (confidence: {})",
                    result.skill().getName(), result.matchedKeyword(), result.confidence());
                return result;
            }
            log.debug("Semantic routing returned low confidence or null, falling back to keyword");
        } catch (Exception e) {
            log.warn("Semantic routing failed, falling back to keyword: {}", e.getMessage());
        }
        
        SkillRouteDto fallback = keywordRouter.route(userInput);
        if (fallback != null) {
            log.info("Keyword router matched: {} via '{}'", fallback.skill().getName(), fallback.matchedKeyword());
        } else {
            log.debug("No skill matched for input: {}", userInput);
        }
        return fallback;
    }
    
    private boolean containsNegativeIndicator(String lowerInput) {
        for (String indicator : KeywordSkillRouter.getNegativeIndicators()) {
            if (lowerInput.contains(indicator.toLowerCase())) {
                return true;
            }
        }
        return false;
    }
    
    private SkillRouteDto routeSemantically(String userInput) {
        String prompt = buildRoutingPrompt(userInput);
        List<Message> messages = List.of(Message.user(prompt));
        String response = callLLM(messages);
        
        if (response == null || response.isBlank()) {
            return null;
        }
        
        return parseLLMResponse(response);
    }
    
    private String buildRoutingPrompt(String userInput) {
        StringBuilder sb = new StringBuilder();
        sb.append("你是一个意图分类器。根据用户输入，判断用户是否**明确**想执行某个技能。\n\n");
        
        sb.append("用户输入：").append(userInput).append("\n\n");
        
        sb.append("可选的技能：\n");
        for (SkillDefinition skill : keywordRouter.getAllSkills()) {
            sb.append("- ").append(skill.getName()).append(": ").append(skill.getDescription()).append("\n");
        }
        
        sb.append("\n判断规则：\n");
        sb.append("1. 如果用户使用了明确的触发词（如\"帮我审查代码\"、\"生成文档\"），confidence >= 0.8\n");
        sb.append("2. 如果用户只是聊天、提问、说\"看看\"、\"聊聊\"、\"问一下\"，confidence <= 0.3\n");
        sb.append("3. 如果用户意图不明确，confidence <= 0.5\n\n");
        
        sb.append("返回 JSON 格式：\n");
        sb.append("{\"skill\": \"技能名称或null\", \"confidence\": 0.0-1.0, \"reason\": \"判断理由\"}\n\n");
        
        sb.append("示例：\n");
        sb.append("用户: \"帮我审查这段代码\" → {\"skill\": \"code_review\", \"confidence\": 0.9, \"reason\": \"用户明确要求审查代码\"}\n");
        sb.append("用户: \"我要提代码了你看看呢\" → {\"skill\": null, \"confidence\": 0.2, \"reason\": \"用户只是想聊聊，包含否定词'看看'\"}\n");
        sb.append("用户: \"解释一下这段代码\" → {\"skill\": null, \"confidence\": 0.3, \"reason\": \"用户只想理解代码，不是执行技能\"}\n\n");
        
        sb.append("只返回 JSON，不要有其他内容。");
        
        return sb.toString();
    }
    
    private String callLLM(List<Message> messages) {
        try {
            CountDownLatch latch = new CountDownLatch(1);
            AtomicReference<String> responseRef = new AtomicReference<>();
            AtomicReference<Exception> errorRef = new AtomicReference<>();
            
            llmClient.chatStream(messages, new LLMClient.StreamHandler() {
                @Override
                public void onEvent(StreamEvent event) {
                    if (event.getType() == StreamEvent.Type.TEXT_DELTA) {
                        responseRef.updateAndGet(r -> r == null ? event.getTextDelta() : r + event.getTextDelta());
                    } else if (event.getType() == StreamEvent.Type.MESSAGE_COMPLETE) {
                        latch.countDown();
                    }
                }
                
                @Override
                public void onError(Exception e) {
                    errorRef.set(e);
                    latch.countDown();
                }
            });
            
            latch.await();
            
            if (errorRef.get() != null) {
                log.warn("LLM call failed: {}", errorRef.get().getMessage());
                return null;
            }
            
            return responseRef.get();
        } catch (Exception e) {
            log.warn("LLM call exception: {}", e.getMessage());
            return null;
        }
    }
    
    private SkillRouteDto parseLLMResponse(String response) {
        String json = extractJson(response);
        if (json == null) {
            return null;
        }
        
        try {
            String skillName = extractJsonString(json, "skill");
            
            if (skillName == null || "null".equals(skillName)) {
                return null;
            }
            
            double confidence = extractJsonDouble(json, "confidence", 0.5);
            String reason = extractJsonString(json, "reason");
            
            for (SkillDefinition skill : keywordRouter.getAllSkills()) {
                if (skill.getName().equals(skillName)) {
                    return SkillRouteDto.semanticMatch(skill, reason != null ? reason : skillName, confidence);
                }
            }
            
            log.warn("LLM returned unknown skill: {}", skillName);
            return null;
            
        } catch (Exception e) {
            log.warn("Failed to parse LLM response: {}", e.getMessage());
            return null;
        }
    }
    
    private String extractJson(String text) {
        int start = text.indexOf("{");
        int end = text.lastIndexOf("}");
        if (start >= 0 && end > start) {
            return text.substring(start, end + 1);
        }
        return null;
    }
    
    private String extractJsonString(String json, String key) {
        String pattern = "\"" + key + "\"\\s*:\\s*\"([^\"]*)\"";
        Matcher matcher = Pattern.compile(pattern).matcher(json);
        if (matcher.find()) {
            return matcher.group(1);
        }
        if (json.contains("\"" + key + "\"\\s*:\\s*null")) {
            return null;
        }
        return null;
    }
    
    private double extractJsonDouble(String json, String key, double defaultValue) {
        String pattern = "\"" + key + "\"\\s*:\\s*([0-9.]+)";
        Matcher matcher = Pattern.compile(pattern).matcher(json);
        if (matcher.find()) {
            try {
                return Double.parseDouble(matcher.group(1));
            } catch (NumberFormatException e) {
                return defaultValue;
            }
        }
        return defaultValue;
    }
    
    public List<SkillDefinition> getAllSkills() {
        return keywordRouter.getAllSkills();
    }
    
    public String getAllSkillSummaries() {
        return keywordRouter.getAllSkillSummaries();
    }
}
