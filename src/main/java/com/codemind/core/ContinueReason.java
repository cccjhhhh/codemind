package com.codemind.core;

public enum ContinueReason {
    NEXT_TURN,
    TOKEN_BUDGET_CONTINUE,
    RECOVERY_COMPACT,
    RECOVERY_ESCALATE,
    RECOVERY_FAILOVER,
    COMPLETE,
    MAX_ITERATIONS,
    ERROR,
    USER_INTERRUPT
}
