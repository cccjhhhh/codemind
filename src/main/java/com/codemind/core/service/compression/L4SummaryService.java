package com.codemind.core.service.compression;

import com.codemind.api.llm.Message;

import java.util.List;

/**
 * L4 全量摘要服务接口。
 *
 * 使用 LLM 对完整的对话历史生成结构化摘要。
 * 【强制】必须保留消息 role 结构：system(摘要) + 最近 N 轮完整消息
 * 【禁止】clearHistory() + addMessage(user(summary))
 */
public interface L4SummaryService {

    /**
     * 对消息列表执行全量摘要。
     *
     * @param messages    当前完整消息列表
     * @param context     会话上下文附加信息（如文件缓存）
     * @return 压缩后的消息列表，格式：
     *         system(原指令) + system([摘要]) + system(正文) + [最近 K 轮原始消息]
     */
    List<Message> summarize(List<Message> messages, Object context);

    /**
     * 保留的最近完整轮次数（不被摘要压缩）。
     * 默认 3 轮。
     */
    default int keepRecentRounds() {
        return 3;
    }
}
