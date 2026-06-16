package com.codemind.agent.statemachine;

import com.codemind.agent.statemachine.HandlerResult;
import com.codemind.agent.engine.ExecutionState;

@FunctionalInterface
public interface StateHandler {
    HandlerResult handle(ExecutionState state);
}
