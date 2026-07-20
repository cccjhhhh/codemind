package com.codemind.evaluation;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.Map;

/**
 * 单次工具调用的详细记录。
 */
public class ToolCallRecord {

    @JsonProperty("tool_name")
    private String toolName;

    @JsonProperty("arguments")
    private Map<String, Object> arguments;

    @JsonProperty("success")
    private boolean success;

    @JsonProperty("output_length")
    private int outputLength;

    @JsonProperty("duration_ms")
    private long durationMs;

    @JsonProperty("error_message")
    private String errorMessage;

    @JsonProperty("timestamp")
    private long timestamp;

    public ToolCallRecord() {}

    public ToolCallRecord(String toolName, Map<String, Object> arguments, boolean success,
                          int outputLength, long durationMs, String errorMessage) {
        this.toolName = toolName;
        this.arguments = arguments;
        this.success = success;
        this.outputLength = outputLength;
        this.durationMs = durationMs;
        this.errorMessage = errorMessage;
        this.timestamp = System.currentTimeMillis();
    }

    // Getters and Setters
    public String getToolName() { return toolName; }
    public void setToolName(String toolName) { this.toolName = toolName; }

    public Map<String, Object> getArguments() { return arguments; }
    public void setArguments(Map<String, Object> arguments) { this.arguments = arguments; }

    public boolean isSuccess() { return success; }
    public void setSuccess(boolean success) { this.success = success; }

    public int getOutputLength() { return outputLength; }
    public void setOutputLength(int outputLength) { this.outputLength = outputLength; }

    public long getDurationMs() { return durationMs; }
    public void setDurationMs(long durationMs) { this.durationMs = durationMs; }

    public String getErrorMessage() { return errorMessage; }
    public void setErrorMessage(String errorMessage) { this.errorMessage = errorMessage; }

    public long getTimestamp() { return timestamp; }
    public void setTimestamp(long timestamp) { this.timestamp = timestamp; }
}
