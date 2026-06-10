package com.codemind.core;

public record QueryConfig(
    int maxIterations,
    long deadlineMs,
    int contextWindowTokens,
    double targetRatio,
    int reservedResponseTokens,
    int maxOutputTokens,
    boolean hasAttemptedReactiveCompact
) {}
