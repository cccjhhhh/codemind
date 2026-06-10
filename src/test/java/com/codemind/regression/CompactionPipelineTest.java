package com.codemind.regression;

import com.codemind.api.llm.Message;
import com.codemind.impl.session.CompactionPipeline;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.junit.jupiter.api.Assertions.*;

class CompactionPipelineTest {

    @TempDir
    Path tempDir;

    private CompactionPipeline pipeline;

    @BeforeEach
    void setUp() {
        pipeline = new CompactionPipeline(
            10,     // maxMessagesBeforeSnip
            2,      // keepRecentToolResults
            100000, // budgetMaxBytes
            tempDir,
            50,     // spillThresholdChars
            "test-session",
            false   // saveTranscripts
        );
    }

    @Test
    void testNoCompactionWhenUnderLimit() {
        List<Message> messages = new ArrayList<>();
        messages.add(Message.user("hello"));
        messages.add(Message.assistant("hi there"));

        List<Message> result = pipeline.run(messages, null);

        assertEquals(2, result.size());
    }

    @Test
    void testL3SpillLargeToolResult() {
        List<Message> messages = new ArrayList<>();
        messages.add(Message.user("test"));
        String largeContent = "x".repeat(100);
        messages.add(Message.tool(largeContent, "tc1"));

        List<Message> result = pipeline.run(messages, null);

        assertEquals(2, result.size());
        String content = result.get(1).getContent();
        assertTrue(content.startsWith("[结果已保存]"));
    }

    @Test
    void testL1SnipCompact() {
        List<Message> messages = new ArrayList<>();
        messages.add(Message.user("first"));
        for (int i = 0; i < 12; i++) {
            messages.add(Message.user("msg-" + i));
            messages.add(Message.assistant("resp-" + i));
        }

        List<Message> result = pipeline.run(messages, null);

        assertTrue(result.size() < messages.size(), "L1 should snip messages");
        boolean hasSnipped = result.stream().anyMatch(
            m -> m.getContent() != null && m.getContent().startsWith("[snipped"));
        assertTrue(hasSnipped, "Should contain [snipped N messages] placeholder");
    }

    @Test
    void testL2MicroCompactOldToolResults() {
        List<Message> messages = new ArrayList<>();
        messages.add(Message.user("test"));
        for (int i = 0; i < 4; i++) {
            messages.add(Message.tool("detailed result content line " + i + " with more than 120 chars "
                + "to trigger the micro compaction logic in L2 layer of the pipeline", "tc-" + i));
        }

        List<Message> result = pipeline.run(messages, null);

        int compacted = 0;
        int preserved = 0;
        for (Message msg : result) {
            if (msg.getRole() == Message.Role.TOOL) {
                if (msg.getContent() != null && msg.getContent().startsWith("[Earlier")) {
                    compacted++;
                } else {
                    preserved++;
                }
            }
        }
        assertEquals(2, compacted);
        assertEquals(2, preserved);
    }

    @Test
    void testL4CallbackInvoked() {
        List<Message> messages = new ArrayList<>();
        messages.add(Message.user("test"));
        messages.add(Message.assistant("response"));

        AtomicBoolean l4Called = new AtomicBoolean(false);
        pipeline.run(messages, () -> l4Called.set(true));

        assertTrue(l4Called.get());
    }

    @Test
    void testEmptyMessageList() {
        List<Message> result = pipeline.run(new ArrayList<>(), null);
        assertTrue(result.isEmpty());
    }

    @Test
    void testConsecutiveFailuresTracking() {
        assertEquals(0, pipeline.getConsecutiveFailures());
        pipeline.resetFailures();
        assertEquals(0, pipeline.getConsecutiveFailures());
    }
}
