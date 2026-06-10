package com.codemind.regression;

import com.codemind.api.llm.ToolCall;
import com.codemind.core.ToolPartitioner;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class ToolPartitionerTest {

    @Test
    void testPartitionMixedTools() {
        List<ToolCall> calls = List.of(
            new ToolCall("1", "Read", Map.of()),
            new ToolCall("2", "Grep", Map.of()),
            new ToolCall("3", "Write", Map.of()),
            new ToolCall("4", "Glob", Map.of()),
            new ToolCall("5", "Bash", Map.of())
        );

        List<List<ToolCall>> batches = ToolPartitioner.partition(calls);

        assertEquals(4, batches.size());
        assertTrue(ToolPartitioner.isParallel(batches.get(0)));
        assertFalse(ToolPartitioner.isParallel(batches.get(1)));
        assertTrue(ToolPartitioner.isParallel(batches.get(2)));
        assertFalse(ToolPartitioner.isParallel(batches.get(3)));
    }

    @Test
    void testAllReadToolsPartitionedAsOneBatch() {
        List<ToolCall> calls = List.of(
            new ToolCall("1", "Read", Map.of()),
            new ToolCall("2", "Glob", Map.of()),
            new ToolCall("3", "Grep", Map.of())
        );

        List<List<ToolCall>> batches = ToolPartitioner.partition(calls);
        assertEquals(1, batches.size());
        assertTrue(ToolPartitioner.isParallel(batches.get(0)));
    }

    @Test
    void testAllSerialToolsGroupedInOneBatch() {
        // Consecutive serial tools are grouped into a single batch
        List<ToolCall> calls = List.of(
            new ToolCall("1", "Write", Map.of()),
            new ToolCall("2", "Bash", Map.of()),
            new ToolCall("3", "Edit", Map.of())
        );

        List<List<ToolCall>> batches = ToolPartitioner.partition(calls);
        assertEquals(1, batches.size());
        assertFalse(ToolPartitioner.isParallel(batches.get(0)));
        assertEquals(3, batches.get(0).size());
    }

    @Test
    void testEmptyList() {
        List<List<ToolCall>> batches = ToolPartitioner.partition(List.of());
        assertTrue(batches.isEmpty());
    }

    @Test
    void testSingleReadTool() {
        List<ToolCall> calls = List.of(
            new ToolCall("1", "Read", Map.of())
        );

        List<List<ToolCall>> batches = ToolPartitioner.partition(calls);
        assertEquals(1, batches.size());
        assertTrue(ToolPartitioner.isParallel(batches.get(0)));
    }

    @Test
    void testSingleSerialTool() {
        List<ToolCall> calls = List.of(
            new ToolCall("1", "Bash", Map.of())
        );

        List<List<ToolCall>> batches = ToolPartitioner.partition(calls);
        assertEquals(1, batches.size());
        assertFalse(ToolPartitioner.isParallel(batches.get(0)));
    }
}
