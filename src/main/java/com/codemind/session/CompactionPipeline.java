package com.codemind.session;

import com.codemind.llm.Message;
import com.codemind.context.Compactor;
import com.codemind.context.ContextCompressionOrchestrator;
import com.codemind.context.L1SnipCompactor;
import com.codemind.context.L2MicroCompactor;
import com.codemind.context.L3SpillCompactor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/**
 * 压缩管线（向后兼容包装）。
 *
 * 内部委托给 {@link ContextCompressionOrchestrator} + 各 Compactor。
 * 【harness 规则 03】所有压缩最终单入口。
 *
 * 新代码应直接使用 {@link ContextCompressionOrchestrator}。
 */
public class CompactionPipeline {

    private static final Logger log = LoggerFactory.getLogger(CompactionPipeline.class);

    private final ContextCompressionOrchestrator orchestrator;
    private final Path spillDir;
    private final String sessionId;
    private final boolean saveTranscripts;

    private int consecutiveFailures = 0;

    public CompactionPipeline(int maxMessagesBeforeSnip, int keepRecentToolResults,
                              int budgetMaxBytes, Path spillDir, int spillThresholdChars,
                              String sessionId, boolean saveTranscripts) {
        List<Compactor> compactors = new ArrayList<>();
        compactors.add(new L1SnipCompactor(maxMessagesBeforeSnip));
        compactors.add(new L2MicroCompactor(keepRecentToolResults));
        compactors.add(new L3SpillCompactor(spillThresholdChars, spillDir, sessionId));
        this.orchestrator = new ContextCompressionOrchestrator(compactors);
        this.spillDir = spillDir;
        this.sessionId = sessionId;
        this.saveTranscripts = saveTranscripts;
    }

    public void resetFailures() { consecutiveFailures = 0; }

    /**
     * 运行完整管线: L1 → L2 → L3 (委托给 ContextCompressionOrchestrator)
     */
    public List<Message> run(List<Message> messages) {
        var result = orchestrator.run(messages, null);

        if (!result.didCompact()) {
            consecutiveFailures++;
        } else {
            consecutiveFailures = 0;
        }

        return result.compressedMessages();
    }

}
