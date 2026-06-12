package com.codemind.core.service.execution;

import com.codemind.core.ContinueReason;

public record HandlerResult(ContinueReason nextReason, boolean countTurn) {
    public static HandlerResult withCount(ContinueReason reason) {
        return new HandlerResult(reason, true);
    }

    public static HandlerResult withoutCount(ContinueReason reason) {
        return new HandlerResult(reason, false);
    }
}
