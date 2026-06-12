package com.codemind.core.service.compression;

/**
 * 压缩管线执行结果。
 *
 * @param compressedMessages 压缩后的消息列表
 * @param didCompact         L1-L3 是否实际执行了压缩
 * @param didSummarize       L4 是否执行了摘要
 * @param originalSize       压缩前消息数
 * @param compressedSize     压缩后消息数
 */
public record CompressionResult(
        java.util.List<com.codemind.api.llm.Message> compressedMessages,
        boolean didCompact,
        boolean didSummarize,
        int originalSize,
        int compressedSize
) {
    public boolean wasEffective() {
        return compressedSize < originalSize;
    }
}
