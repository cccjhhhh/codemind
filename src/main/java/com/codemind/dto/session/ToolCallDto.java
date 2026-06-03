package com.codemind.dto.session;

import com.codemind.api.llm.ToolCall;

import java.util.Map;

/**
 * 工具调用数据传输对象
 * 
 * 用于 JSON 序列化/反序列化工具调用。
 */
public class ToolCallDto {
    
    private String id;
    private String name;
    private Map<String, Object> arguments;
    
    // 空的构造函数用于反序列化
    public ToolCallDto() {}
    
    public ToolCallDto(String id, String name, Map<String, Object> arguments) {
        this.id = id;
        this.name = name;
        this.arguments = arguments;
    }
    
    public String getId() {
        return id;
    }
    
    public String getName() {
        return name;
    }
    
    public Map<String, Object> getArguments() {
        return arguments;
    }
    
    public void setId(String id) {
        this.id = id;
    }
    
    public void setName(String name) {
        this.name = name;
    }
    
    public void setArguments(Map<String, Object> arguments) {
        this.arguments = arguments;
    }
    
    /**
     * 从 API ToolCall 转换为此 DTO
     */
    public static ToolCallDto fromToolCall(ToolCall toolCall) {
        return new ToolCallDto(
            toolCall.getId(),
            toolCall.getName(),
            toolCall.getArguments()
        );
    }
    
    /**
     * 转换为 API ToolCall
     */
    public ToolCall toToolCall() {
        return new ToolCall(id, name, arguments);
    }
}