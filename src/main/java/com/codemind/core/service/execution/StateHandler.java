package com.codemind.core.service.execution;

@FunctionalInterface
public interface StateHandler {
    HandlerResult handle(ExecutionState state);
}
