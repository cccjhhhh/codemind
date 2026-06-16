package com.codemind.context;

import com.codemind.llm.Message;

import java.util.List;
import java.util.Set;

/**
 * 压缩策略接口（策略模式）。
 *
 * 每个 Compactor 负责一种特定的压缩操作，
 * 通过 order() 决定执行顺序，管线依次执行。
 *
 * 【harness 规则 03-compression-single-entry】
 * 所有压缩经过 ContextCompressionOrchestrator 统一入口，
 * Compactor 之间通过 protectedReadIndices 共享已读文件保护信息。
 */
public interface Compactor {

    /**
     * 执行顺序。值越小越先执行。
     * L1=10, L2=20, L3=30, L4=40
     */
    int order();

    /**
     * 对消息列表执行压缩。
     *
     * @param messages             当前消息列表
     * @param protectedReadIndices 受保护的 Read 结果索引（不可压缩/落盘）
     * @return 压缩后的消息列表
     */
    List<Message> compact(List<Message> messages, Set<Integer> protectedReadIndices);

    /**
     * 压缩器名称，用于日志和监控。
     */
    default String name() {
        return getClass().getSimpleName();
    }
}
