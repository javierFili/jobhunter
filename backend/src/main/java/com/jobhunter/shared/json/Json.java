package com.jobhunter.shared.json;

import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.DeserializationFeature;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/**
 * Static JSON helper for places outside Spring injection (JPA converters, value objects).
 * Lenient on unknown properties because stored JSON may come from older versions.
 */
public final class Json {

    public static final JsonMapper MAPPER = JsonMapper.builder()
            .disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES)
            .build();

    private Json() {
    }

    public static String write(Object value) {
        return value == null ? null : MAPPER.writeValueAsString(value);
    }

    public static <T> T read(String json, Class<T> type) {
        return json == null || json.isBlank() ? null : MAPPER.readValue(json, type);
    }

    public static <T> T read(String json, TypeReference<T> type) {
        return json == null || json.isBlank() ? null : MAPPER.readValue(json, type);
    }

    public static JsonNode tree(String json) {
        return MAPPER.readTree(json);
    }
}
