package com.ke.bella.files.utils;

import java.util.Map;

import org.apache.commons.lang3.StringUtils;

import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.databind.deser.std.NumberDeserializers;
import com.fasterxml.jackson.databind.module.SimpleModule;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;

public class JsonUtils {
    private static ObjectMapper mapper = new ObjectMapper();
    private static ObjectMapper mapper2 = new ObjectMapper();
    static {
        mapper.configure(JsonParser.Feature.ALLOW_SINGLE_QUOTES, true);
        mapper.disable(SerializationFeature.FAIL_ON_EMPTY_BEANS);
        mapper.disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES);
        mapper.setSerializationInclusion(com.fasterxml.jackson.annotation.JsonInclude.Include.NON_NULL);
        mapper.disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
        mapper.registerModule(new JavaTimeModule());

        mapper.enable(DeserializationFeature.USE_BIG_DECIMAL_FOR_FLOATS);
        SimpleModule module = new SimpleModule();
        module.addDeserializer(Number.class, new NumberDeserializers.BigDecimalDeserializer());
        mapper.registerModule(module);
    }

    @SuppressWarnings("rawtypes")
    public static <T> T convertValue(Map source, Class<T> clazz) {
        return mapper.convertValue(source, clazz);
    }

    public static <T> T fromJson(String json, Class<T> clazz) {
        try {
            if(StringUtils.isEmpty(json)) {
                return null;
            }
            return mapper.readValue(json, clazz);
        } catch (Exception e) {
            throw new IllegalArgumentException(e);
        }
    }

    public static <T> T fromJson(String json, TypeReference<T> clazz) {
        try {
            if(StringUtils.isEmpty(json)) {
                return null;
            }
            return mapper.readValue(json, clazz);
        } catch (Exception e) {
            throw new IllegalArgumentException(e);
        }
    }

    public static String toJson(Object obj) {
        try {
            return mapper.writeValueAsString(obj);
        } catch (JsonProcessingException e) {
            throw new IllegalArgumentException(e);
        }
    }

    public static String toJsonWithNull(Object obj) {
        try {
            return mapper2.writeValueAsString(obj);
        } catch (JsonProcessingException e) {
            throw new IllegalArgumentException(e);
        }
    }
}
