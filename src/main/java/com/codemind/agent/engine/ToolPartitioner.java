package com.codemind.agent.engine;

import com.codemind.llm.ToolCall;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

public class ToolPartitioner {

    /**
     * 可并行执行的工具集合。
     * 包括只读安全工具和Task工具（Task有独立SessionContext，可安全并行）。
     */
    private static final Set<String> PARALLEL_SAFE_TOOLS = Set.of(
        "Read", "Grep", "Glob", "WebFetch",
        "Task"  // Task子Agent有独立上下文，可并行执行
    );

    public static List<List<ToolCall>> partition(List<ToolCall> toolCalls) {
        List<List<ToolCall>> batches = new ArrayList<>();
        List<ToolCall> currentBatch = null;
        boolean currentIsParallel = false;

        for (ToolCall tc : toolCalls) {
            boolean isParallelSafe = PARALLEL_SAFE_TOOLS.contains(tc.getName());
            if (currentBatch == null || currentIsParallel != isParallelSafe) {
                currentBatch = new ArrayList<>();
                batches.add(currentBatch);
                currentIsParallel = isParallelSafe;
            }
            currentBatch.add(tc);
        }
        return batches;
    }

    public static boolean isParallel(List<ToolCall> batch) {
        if (batch.isEmpty()) return false;
        return PARALLEL_SAFE_TOOLS.contains(batch.get(0).getName());
    }
}
