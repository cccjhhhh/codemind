package com.codemind.impl.skill;

import com.codemind.api.llm.LLMClient;
import com.codemind.api.llm.Message;
import com.codemind.api.llm.StreamEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;

/**
 * 语义 Skill 路由器（基于 LLM 判断用户意图 - Tier 3）
 * 
 * 工作原理：
 * 1. 先检查否定关键词（快速路径，不调用 LLM）
 * 2. 将用户输入 + 所有 Skill 的 description 发送给 LLM
 * 3. LLM 判断用户意图，返回应该触发的 Skill + confidence（或 null 表示不触发）
 * 
 * 设计优势：
 * - 支持自然语言触发：用户说"帮我审查这段代码"能触发 code_review
 * - 置信度阈值：只有 confidence >= 0.7 才触发 Skill
 * - 否定词快速路径：包含"看看"、"聊聊"等词直接返回 null
 * 
 * 降级策略：
 * - 如果 LLM 不可用或调用失败，降级到关键词匹配
 * - 如果 LLM 返回无效结果，降级到关键词匹配
 * 
 * 参考：LangChain confidence threshold, Claude Code invocation control
 */
public class SemanticSkillRouter {
    
    private static final Logger log = LoggerFactory.getLogger(SemanticSkillRouter.class);
    
    /** 置信度阈值（只有 >= 此值才触发 Skill） */
    private static final double CONFIDENCE_THRESHOLD = SkillRoute.CONFIDENCE_THRESHOLD;
    
    private final LLMClient llmClient;
    private final KeywordSkillRouter keywordRouter;  // 降级用的关键词路由
    
    public SemanticSkillRouter(LLMClient llmClient, List<SkillDefinition> skills) {
        this.llmClient = llmClient;
        this.keywordRouter = new KeywordSkillRouter(skills);
    }
    
    /**
     * 路由用户输入到对应的 Skill（语义判断）
     * 
     * @param userInput 用户输入
     * @return 路由结果，如果不需要触发 Skill 返回 null
     */
    public SkillRoute route(String userInput) {
        if (userInput == null || userInput.isBlank()) {
            return null;
        }
        
        // 快速路径：先检查否定关键词（不调用 LLM）
        if (containsNegativeIndicator(userInput.toLowerCase())) {
            log.debug("Negative indicator detected, skipping semantic routing");
            return null;
        }
        
        // 尝试语义路由
        try {
            log.debug("Attempting semantic routing for: {}", userInput);
            SkillRoute result = routeSemantically(userInput);
            if (result != null && result.shouldExecute()) {
                log.info("Semantic router matched: {} via '{}' (confidence: {})", 
                    result.skill().getName(), result.matchedKeyword(), result.confidence());
                return result;
            }
            log.debug("Semantic routing returned low confidence or null, falling back to keyword");
        } catch (Exception e) {
            log.warn("Semantic routing failed, falling back to keyword: {}", e.getMessage());
        }
        
        // 降级到关键词匹配
        SkillRoute fallback = keywordRouter.route(userInput);
        if (fallback != null) {
            log.info("Keyword router matched: {} via '{}'", fallback.skill().getName(), fallback.matchedKeyword());
        } else {
            log.debug("No skill matched for input: {}", userInput);
        }
        return fallback;
    }
    
    /**
     * 检查是否包含否定关键词
     */
    private boolean containsNegativeIndicator(String lowerInput) {
        for (String indicator : KeywordSkillRouter.getNegativeIndicators()) {
            if (lowerInput.contains(indicator.toLowerCase())) {
                return true;
            }
        }
        return false;
    }
    
    /**
     * 语义路由核心逻辑
     */
    private SkillRoute routeSemantically(String userInput) {
        // 构建 prompt
        String prompt = buildRoutingPrompt(userInput);
        
        // 调用 LLM
        List<Message> messages = List.of(Message.user(prompt));
        
        // 同步调用（简单实现）
        String response = callLLM(messages);
        
        if (response == null || response.isBlank()) {
            return null;
        }
        
        // 解析 LLM 响应
        return parseLLMResponse(response);
    }
    
    /**
     * 构建路由 prompt
     * 
     * 关键改进：
     * 1. 要求返回置信度（confidence）
     * 2. 明确触发条件
     * 3. 明确不触发的情况
     */
    private String buildRoutingPrompt(String userInput) {
        StringBuilder sb = new StringBuilder();
        sb.append("你是一个意图分类器。根据用户输入，判断用户是否**明确**想执行某个技能。\n\n");
        
        sb.append("用户输入：").append(userInput).append("\n\n");
        
        sb.append("可选的技能：\n");
        for (SkillDefinition skill : keywordRouter.getAllSkills()) {
            sb.append("- ").append(skill.getName()).append(": ").append(skill.getDescription()).append("\n");
        }
        
        sb.append("\n");
        sb.append("判断规则：\n");
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
    
    /**
     * 调用 LLM
     */
    private String callLLM(List<Message> messages) {
        try {
            // 使用简单的同步调用
            var latch = new java.util.concurrent.CountDownLatch(1);
            var responseRef = new java.util.concurrent.atomic.AtomicReference<String>();
            var errorRef = new java.util.concurrent.atomic.AtomicReference<Exception>();
            
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
    
    /**
     * 解析 LLM 响应
     */
    private SkillRoute parseLLMResponse(String response) {
        // 提取 JSON
        String json = extractJson(response);
        if (json == null) {
            return null;
        }
        
        try {
            // 简单解析 JSON（不依赖 Jackson）
            String skillName = extractJsonString(json, "skill");
            
            if (skillName == null || "null".equals(skillName)) {
                return null;
            }
            
            // 提取置信度
            double confidence = extractJsonDouble(json, "confidence", 0.5);
            
            String reason = extractJsonString(json, "reason");
            
            // 查找对应的 Skill
            for (SkillDefinition skill : keywordRouter.getAllSkills()) {
                if (skill.getName().equals(skillName)) {
                    return SkillRoute.semanticMatch(skill, reason != null ? reason : skillName, confidence);
                }
            }
            
            log.warn("LLM returned unknown skill: {}", skillName);
            return null;
            
        } catch (Exception e) {
            log.warn("Failed to parse LLM response: {}", e.getMessage());
            return null;
        }
    }
    
    /**
     * 从文本中提取 JSON
     */
    private String extractJson(String text) {
        int start = text.indexOf("{");
        int end = text.lastIndexOf("}");
        if (start >= 0 && end > start) {
            return text.substring(start, end + 1);
        }
        return null;
    }
    
    /**
     * 简单 JSON 解析：提取字符串值
     */
    private String extractJsonString(String json, String key) {
        String pattern = "\"" + key + "\"\\s*:\\s*\"([^\"]*)\"";
        java.util.regex.Matcher matcher = java.util.regex.Pattern.compile(pattern).matcher(json);
        if (matcher.find()) {
            return matcher.group(1);
        }
        // 尝试 null 值
        if (json.contains("\"" + key + "\"\\s*:\\s*null")) {
            return null;
        }
        return null;
    }
    
    /**
     * 简单 JSON 解析：提取数字值
     */
    private double extractJsonDouble(String json, String key, double defaultValue) {
        String pattern = "\"" + key + "\"\\s*:\\s*([0-9.]+)";
        java.util.regex.Matcher matcher = java.util.regex.Pattern.compile(pattern).matcher(json);
        if (matcher.find()) {
            try {
                return Double.parseDouble(matcher.group(1));
            } catch (NumberFormatException e) {
                return defaultValue;
            }
        }
        return defaultValue;
    }
    
    /**
     * 获取所有 Skill（用于降级路由）
     */
    public List<SkillDefinition> getAllSkills() {
        return keywordRouter.getAllSkills();
    }
    
    /**
     * 获取所有 Skill 的简介（用于系统提示）
     */
    public String getAllSkillSummaries() {
        return keywordRouter.getAllSkillSummaries();
    }
}