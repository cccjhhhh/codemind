package com.codemind.impl.compression;

import com.codemind.api.llm.LLMClient;
import com.codemind.api.llm.LLMResponse;
import com.codemind.api.llm.Message;
import com.codemind.core.service.compression.L4SummaryService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;

/**
 * L4 全量摘要压缩器。
 *
 * 使用 LLM 对完整对话历史生成结构化摘要。
 *
 * 【强制】保留消息 role 结构，禁止 clearHistory()
 * 压缩结果格式：
 *   system(原始指令) ← 保留
 *   system("[Previous conversation compressed]")
 *   system(summary_body)
 *   user(最近一轮用户输入) ← 保留最近 N 轮
 *   assistant(...) + tool(...)
 *
 * 【harness 规则 05-message-structure】
 * 不得破坏 ASSISTANT-TOOL 配对关系。
 */
public class L4SummaryCompactor implements L4SummaryService {

    private static final Logger log = LoggerFactory.getLogger(L4SummaryCompactor.class);

    private final LLMClient llmClient;
    private final int keepRecentRounds;

    public L4SummaryCompactor(LLMClient llmClient, int keepRecentRounds) {
        this.llmClient = llmClient;
        this.keepRecentRounds = keepRecentRounds;
    }

    public L4SummaryCompactor(LLMClient llmClient) {
        this(llmClient, 3);
    }

    @Override
    public int keepRecentRounds() {
        return keepRecentRounds;
    }

    @Override
    public List<Message> summarize(List<Message> messages, Object context) {
        if (messages == null || messages.isEmpty()) return messages;

        // 1. 分离 system 消息和普通消息
        Message systemMessage = null;
        List<Message> conversationMessages = new ArrayList<>();
        for (Message msg : messages) {
            if (msg.getRole() == Message.Role.SYSTEM && systemMessage == null) {
                systemMessage = msg;
            } else {
                conversationMessages.add(msg);
            }
        }

        // 2. 保留最近 N 轮原始消息
        List<Message> recentMessages = keepRecentRounds(conversationMessages);

        // 3. 构建摘要 prompt（对需要压缩的部分生成摘要）
        List<Message> toSummarize = conversationMessages.subList(0,
                Math.max(0, conversationMessages.size() - countRecentItems(conversationMessages)));
        if (toSummarize.isEmpty()) {
            // 消息量很小，不需要摘要
            return messages;
        }

        String summary = generateSummary(toSummarize);
        if (summary == null || summary.isBlank()) {
            log.warn("L4 摘要生成失败，返回原始消息");
            return messages;
        }

        // 4. 构建结构化结果
        List<Message> result = new ArrayList<>();
        if (systemMessage != null) {
            result.add(systemMessage); // 保留原始 system prompt
        }
        // 压缩标识 + 摘要正文作为 system 消息
        result.add(Message.system("[Previous conversation compressed]"));
        result.add(Message.system(summary));
        // 最近 N 轮完整添加
        result.addAll(recentMessages);

        log.info("L4 摘要: {} 条 → {} 条 (保留最近 {} 轮)",
                messages.size(), result.size(), keepRecentRounds);
        return result;
    }

    /**
     * 保留最近 keepRecentRounds 轮完整的 ASSISTANT+TOOL 配对消息。
     */
    private List<Message> keepRecentRounds(List<Message> messages) {
        List<Message> result = new ArrayList<>();
        int roundsFound = 0;

        for (int i = messages.size() - 1; i >= 0 && roundsFound < keepRecentRounds; i--) {
            Message msg = messages.get(i);
            result.add(0, msg);
            if (msg.getRole() == Message.Role.ASSISTANT) {
                roundsFound++;
            }
        }

        // 如果保留的消息覆盖了全部，返回原列表
        if (result.size() >= messages.size()) {
            return messages;
        }
        return result;
    }

    /**
     * 计算在 keepRecentRounds 范围内的消息数量。
     */
    private int countRecentItems(List<Message> messages) {
        int count = 0;
        int rounds = 0;
        for (int i = messages.size() - 1; i >= 0 && rounds < keepRecentRounds; i--) {
            count++;
            if (messages.get(i).getRole() == Message.Role.ASSISTANT) {
                rounds++;
            }
        }
        return count;
    }

    private String generateSummary(List<Message> toSummarize) {
        try {
            String conversation = toSummarize.toString();
            if (conversation.length() > 500_000) {
                conversation = conversation.substring(conversation.length() - 500_000);
            }

            String prompt = "Summarize this coding-agent conversation so work can continue.\n"
                    + "Preserve:\n"
                    + "1. Current goal / 当前目标\n"
                    + "2. Key findings and decisions / 关键发现与决策\n"
                    + "3. Files read and their KEY CONTENTS\n"
                    + "4. Files changed and what was modified / 已改文件及修改内容\n"
                    + "5. Remaining work / 剩余工作\n"
                    + "6. User constraints and preferences / 用户约束\n\n"
                    + "Conversation:\n" + conversation;

            LLMResponse response = llmClient.chat(List.of(
                    Message.system("You are a conversation summarizer."),
                    Message.user(prompt)
            ));

            return response.getContent();

        } catch (Exception e) {
            log.error("L4 摘要生成异常: {}", e.getMessage());
            return null;
        }
    }
}
