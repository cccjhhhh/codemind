package com.codemind.impl.util;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;

import java.io.IOException;

/**
 * ObjectMapper 工厂类
 *
 * 提供共享的 ObjectMapper 实例，避免重复创建。
 *
 * 设计原则：
 * - 线程安全：Jackson 2.x 的 ObjectMapper 是线程安全的（只读操作）
 * - 性能优化：复用配置好的实例
 * - 配置统一：所有模块使用相同的 JSON 配置
 *
 * @see <a href="https://github.com/FasterXML/jackson-docs/wiki/JacksonFeatures">Jackson 特性</a>
 */
public final class ObjectMapperFactory {

    /** JSON ObjectMapper - 通用配置 */
    private static final ObjectMapper JSON_MAPPER = createJsonMapper();

    /** YAML ObjectMapper */
    private static final ObjectMapper YAML_MAPPER = createYamlMapper();

    /**
     * 私有构造函数，防止实例化
     */
    private ObjectMapperFactory() {
        throw new UnsupportedOperationException("Utility class cannot be instantiated");
    }

    /**
     * 创建配置好的 JSON ObjectMapper
     */
    private static ObjectMapper createJsonMapper() {
        ObjectMapper mapper = new ObjectMapper();
        // 序列化配置
        mapper.configure(SerializationFeature.FAIL_ON_EMPTY_BEANS, false);
        mapper.configure(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS, false);
        // 反序列化配置
        mapper.configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);
        mapper.configure(DeserializationFeature.ACCEPT_SINGLE_VALUE_AS_ARRAY, true);
        // 注册 Java 8 时间模块
        mapper.registerModule(new JavaTimeModule());
        return mapper;
    }

    /**
     * 创建配置好的 YAML ObjectMapper
     */
    private static ObjectMapper createYamlMapper() {
        ObjectMapper mapper = new ObjectMapper(new YAMLFactory());
        mapper.configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);
        return mapper;
    }

    /**
     * 获取 JSON ObjectMapper 实例
     *
     * @return 配置好的 ObjectMapper
     */
    public static ObjectMapper json() {
        return JSON_MAPPER;
    }

    /**
     * 获取 YAML ObjectMapper 实例
     *
     * @return 配置好的 ObjectMapper
     */
    public static ObjectMapper yaml() {
        return YAML_MAPPER;
    }

    /**
     * 安全地将对象序列化为 JSON 字符串
     *
     * @param obj 要序列化的对象
     * @return JSON 字符串，失败返回 null
     */
    public static String toJson(Object obj) {
        if (obj == null) {
            return null;
        }
        try {
            return JSON_MAPPER.writeValueAsString(obj);
        } catch (JsonProcessingException e) {
            return null;
        }
    }

    /**
     * 安全地将 JSON 字符串反序列化为对象
     *
     * @param json JSON 字符串
     * @param clazz 目标类型
     * @return 反序列化的对象，失败返回 null
     */
    public static <T> T fromJson(String json, Class<T> clazz) {
        if (json == null || json.isEmpty()) {
            return null;
        }
        try {
            return JSON_MAPPER.readValue(json, clazz);
        } catch (IOException e) {
            return null;
        }
    }

    /**
     * 安全地将 YAML 字符串反序列化为对象
     *
     * @param yaml YAML 字符串
     * @param clazz 目标类型
     * @return 反序列化的对象，失败返回 null
     */
    public static <T> T fromYaml(String yaml, Class<T> clazz) {
        if (yaml == null || yaml.isEmpty()) {
            return null;
        }
        try {
            return YAML_MAPPER.readValue(yaml, clazz);
        } catch (IOException e) {
            return null;
        }
    }
}
