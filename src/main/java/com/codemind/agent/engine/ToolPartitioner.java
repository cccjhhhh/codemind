package com.codemind.agent.engine;

import com.codemind.llm.ToolCall;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

public class ToolPartitioner {

    private static final Set<String> READ_SAFE_TOOLS = Set.of(
        "Read", "Grep", "Glob", "WebFetch"
    );

    public static List<List<ToolCall>> partition(List<ToolCall> toolCalls) {
        List<List<ToolCall>> batches = new ArrayList<>();
        List<ToolCall> currentBatch = null;
        boolean currentIsParallel = false;

        for (ToolCall tc : toolCalls) {
            boolean isReadSafe = READ_SAFE_TOOLS.contains(tc.getName());
            if (currentBatch == null || currentIsParallel != isReadSafe) {
                currentBatch = new ArrayList<>();
                batches.add(currentBatch);
                currentIsParallel = isReadSafe;
            }
            currentBatch.add(tc);
        }
        return batches;
    }

    public static boolean isParallel(List<ToolCall> batch) {
        if (batch.isEmpty()) return false;
        return READ_SAFE_TOOLS.contains(batch.get(0).getName());
    }
}
