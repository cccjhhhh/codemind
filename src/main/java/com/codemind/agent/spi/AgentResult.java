package com.codemind.agent.spi;

public class AgentResult {

    private final boolean success;
    private final String message;

    private AgentResult(boolean success, String message) {
        this.success = success;
        this.message = message;
    }

    public static AgentResult success(String message) {
        return new AgentResult(true, message);
    }

    public static AgentResult failure(String error) {
        return new AgentResult(false, error);
    }

    public boolean isSuccess() {
        return success;
    }

    public String getError() {
        return message;
    }

    public String getMessage() {
        return message;
    }
}
