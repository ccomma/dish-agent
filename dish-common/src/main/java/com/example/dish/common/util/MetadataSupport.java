package com.example.dish.common.util;

import org.apache.commons.lang3.StringUtils;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 元数据 Map 组装工具。
 */
public final class MetadataSupport {

    private MetadataSupport() {
    }

    public static Map<String, Object> mapOfNonNull(Object... keyValues) {
        Map<String, Object> metadata = new LinkedHashMap<>();
        for (int i = 0; i < keyValues.length - 1; i += 2) {
            Object key = keyValues[i];
            Object value = keyValues[i + 1];
            if (key instanceof String text && value != null) {
                metadata.put(text, value);
            }
        }
        return metadata;
    }

    public static Map<String, Object> merge(Map<String, Object> left, Map<String, Object> right) {
        LinkedHashMap<String, Object> merged = new LinkedHashMap<>();
        if (left != null) {
            merged.putAll(left);
        }
        if (right != null) {
            merged.putAll(right);
        }
        return Map.copyOf(merged);
    }

    public static Map<String, Object> mergeTraceId(Map<String, Object> current, String traceId) {
        Map<String, Object> metadata = new LinkedHashMap<>(current == null ? Map.of() : current);
        if (StringUtils.isNotBlank(traceId)) {
            metadata.put("traceId", traceId);
        }
        return metadata;
    }
}
