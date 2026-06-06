package com.codemind.api.tool;

import com.codemind.api.safety.PermissionLevel;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.codemind.impl.util.ObjectMapperFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Map;
import java.util.Objects;
import java.util.Optional;

/**
 * 工具基类
 *
 * 提供公共的工具方法实现，包括：
 * - 统一的错误处理
 * - 参数验证
 * - JSON Schema 构建
 *
 * 设计原则：
 * - 模板方法模式：定义执行骨架，子类实现具体逻辑
 * - 统一异常处理：避免重复的 try-catch 代码
 * - 日志记录：统一使用 SLF4J
 *
 * @see Tool
 */
public abstract class BaseTool implements Tool {

    protected final Logger log = LoggerFactory.getLogger(getClass());

    /** 共享的 ObjectMapper 实例 */
    protected static final ObjectMapper JSON = ObjectMapperFactory.json();

    @Override
    public final ToolResult execute(Map<String, Object> params) {
        try {
            // 参数验证
            if (params == null) {
                return ToolResult.failure("参数不能为 null");
            }

            // 执行具体逻辑
            return doExecute(params);

        } catch (IllegalArgumentException e) {
            log.warn("参数验证失败: {}", e.getMessage());
            return ToolResult.failure("参数无效: " + e.getMessage());
        } catch (UnsupportedOperationException e) {
            log.warn("操作不支持: {}", e.getMessage());
            return ToolResult.failure("操作不支持: " + e.getMessage());
        } catch (Exception e) {
            log.error("工具执行失败: {}", getName(), e);
            return ToolResult.failure("执行失败: " + e.getMessage());
        }
    }

    /**
     * 执行具体的工具逻辑
     *
     * 子类实现此方法，无需处理异常（由基类统一处理）
     *
     * @param params 工具参数
     * @return 执行结果
     */
    protected abstract ToolResult doExecute(Map<String, Object> params);

    @Override
    public PermissionLevel getDefaultPermission() {
        return PermissionLevel.ALLOW;
    }

    @Override
    public Optional<String> getDeprecatedName() {
        return Optional.empty();
    }

    // ==================== 辅助方法 ====================

    /**
     * 获取字符串参数
     *
     * @param params 参数 Map
     * @param key 参数键
     * @return 参数值，不存在返回 null
     */
    protected String getStringParam(Map<String, Object> params, String key) {
        Object value = params.get(key);
        return value != null ? value.toString() : null;
    }

    /**
     * 获取必需的字符串参数
     *
     * @param params 参数 Map
     * @param key 参数键
     * @return 参数值
     * @throws IllegalArgumentException 如果参数不存在或为空
     */
    protected String getRequiredStringParam(Map<String, Object> params, String key) {
        String value = getStringParam(params, key);
        if (value == null || value.isEmpty()) {
            throw new IllegalArgumentException("参数 '" + key + "' 是必需的");
        }
        return value;
    }

    /**
     * 获取整数参数
     *
     * @param params 参数 Map
     * @param key 参数键
     * @param defaultValue 默认值
     * @return 参数值
     */
    protected int getIntParam(Map<String, Object> params, String key, int defaultValue) {
        Object value = params.get(key);
        if (value instanceof Number) {
            return ((Number) value).intValue();
        }
        if (value instanceof String) {
            try {
                return Integer.parseInt((String) value);
            } catch (NumberFormatException e) {
                return defaultValue;
            }
        }
        return defaultValue;
    }

    /**
     * 创建基本的输入 Schema
     *
     * @return ObjectNode 可继续添加属性
     */
    protected ObjectNode createBaseSchema() {
        ObjectNode schema = JSON.createObjectNode();
        schema.put("type", "object");
        return schema;
    }

    /**
     * 添加字符串属性到 Schema
     *
     * @param properties properties 节点
     * @param name 属性名
     * @param description 属性描述
     * @return 创建的属性节点
     */
    protected ObjectNode addStringProperty(ObjectNode properties, String name, String description) {
        ObjectNode prop = properties.putObject(name);
        prop.put("type", "string");
        prop.put("description", description);
        return prop;
    }

    /**
     * 添加整数属性到 Schema
     *
     * @param properties properties 节点
     * @param name 属性名
     * @param description 属性描述
     * @return 创建的属性节点
     */
    protected ObjectNode addIntegerProperty(ObjectNode properties, String name, String description) {
        ObjectNode prop = properties.putObject(name);
        prop.put("type", "integer");
        prop.put("description", description);
        return prop;
    }

    /**
     * 验证参数非空
     *
     * @param value 要验证的值
     * @param paramName 参数名
     * @throws IllegalArgumentException 如果值为 null
     */
    protected void validateNotNull(Object value, String paramName) {
        Objects.requireNonNull(value, "参数 '" + paramName + "' 不能为 null");
    }

    /**
     * 验证字符串参数非空
     *
     * @param value 要验证的值
     * @param paramName 参数名
     * @throws IllegalArgumentException 如果值为 null 或空
     */
    protected void validateNotEmpty(String value, String paramName) {
        if (value == null || value.isEmpty()) {
            throw new IllegalArgumentException("参数 '" + paramName + "' 不能为空");
        }
    }
}
