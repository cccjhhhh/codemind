package com.codemind.agent.pattern.planexec.handler;

import com.codemind.agent.pattern.planexec.*;
import com.codemind.llm.ToolCall;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;

/**
 * 简化版 JSON 计划解析器，使用 Jackson。
 *
 * <p>从 LLM 返回的文本中提取 JSON 并解析为 {@link ExecutionPlan}。</p>
 */
class SimplePlanParser {

    private static final Logger log = LoggerFactory.getLogger(SimplePlanParser.class);
    private static final ObjectMapper MAPPER = new ObjectMapper();

    static ExecutionPlan parse(String jsonText) {
        String json = extractJsonObject(jsonText);
        if (json == null) {
            log.warn("无法从响应中提取 JSON: {}", jsonText.substring(0, Math.min(200, jsonText.length())));
            return null;
        }
        try {
            JsonNode root = MAPPER.readTree(json);

            String goal = root.has("goal") ? root.get("goal").asText("未知任务") : "未知任务";
            int maxSteps = root.has("max_steps") ? root.get("max_steps").asInt(20) : 20;
            long budget = root.has("estimated_token_budget") ? root.get("estimated_token_budget").asLong(50000L) : 50000L;

            JsonNode stepsNode = root.get("steps");
            List<PlanStep> steps = new ArrayList<>();
            if (stepsNode != null && stepsNode.isArray()) {
                for (int i = 0; i < stepsNode.size(); i++) {
                    JsonNode step = stepsNode.get(i);
                    steps.add(parseStep(step, i));
                }
            }

            return new ExecutionPlan(goal, steps, maxSteps, budget,
                PlanContext.EMPTY, System.currentTimeMillis());
        } catch (Exception e) {
            log.error("JSON 解析失败: {}", e.getMessage());
            return null;
        }
    }

    private static PlanStep parseStep(JsonNode step, int index) {
        String id = step.has("id") ? step.get("id").asText("step-" + (index + 1))
                                   : "step-" + (index + 1);
        String description = step.has("description") ? step.get("description").asText("") : "";
        String expectedResult = step.has("expected_result") ? step.get("expected_result").asText(null) : null;

        StepConfidence confidence = StepConfidence.MEDIUM;
        if (step.has("confidence")) {
            String c = step.get("confidence").asText("MEDIUM").toUpperCase();
            confidence = switch (c) {
                case "HIGH" -> StepConfidence.HIGH;
                case "LOW" -> StepConfidence.LOW;
                default -> StepConfidence.MEDIUM;
            };
        }

        StepMode mode = StepMode.SERIAL;
        if (step.has("mode")) {
            String m = step.get("mode").asText("SERIAL").toUpperCase();
            mode = switch (m) {
                case "PARALLEL" -> StepMode.PARALLEL;
                case "SUB_AGENT" -> StepMode.SUB_AGENT;
                default -> StepMode.SERIAL;
            };
        }

        int retryBudget = step.has("retry_budget") ? step.get("retry_budget").asInt(2) : 2;

        List<String> dependsOn = new ArrayList<>();
        if (step.has("depends_on") && step.get("depends_on").isArray()) {
            for (JsonNode dep : step.get("depends_on")) {
                dependsOn.add(dep.asText());
            }
        }

        List<ToolCall> tools = parseToolCalls(step.get("tools"));

        return new PlanStep(id, description, tools, expectedResult,
            confidence, dependsOn, retryBudget, mode);
    }

    private static List<ToolCall> parseToolCalls(JsonNode toolsNode) {
        if (toolsNode == null || !toolsNode.isArray()) return List.of();
        List<ToolCall> result = new ArrayList<>();
        for (int i = 0; i < toolsNode.size(); i++) {
            JsonNode tc = toolsNode.get(i);
            if (!tc.has("name")) continue;
            String name = tc.get("name").asText("");
            Map<String, Object> args = new LinkedHashMap<>();
            JsonNode argsNode = tc.has("arguments") ? tc.get("arguments") : tc.get("args");
            if (argsNode != null && argsNode.isObject()) {
                argsNode.fields().forEachRemaining(e -> args.put(e.getKey(), e.getValue().toString()));
            }
            result.add(new ToolCall("tc-" + i, name, args));
        }
        return result;
    }

    /**
     * 从 LLM 返回文本中提取 JSON 对象字符串。
     */
    static String extractJsonObject(String text) {
        if (text == null) return null;
        // 1. 尝试 ```json ... ``` 块
        int start = text.indexOf("```");
        if (start >= 0) {
            int jsonStart = text.indexOf('{', start);
            int jsonEnd = text.lastIndexOf('}');
            if (jsonStart >= 0 && jsonEnd > jsonStart) {
                return text.substring(jsonStart, jsonEnd + 1);
            }
        }
        // 2. 直接找最外层 JSON 对象
        int first = text.indexOf('{');
        int last = text.lastIndexOf('}');
        return (first >= 0 && last > first) ? text.substring(first, last + 1) : null;
    }
}
