package com.example.dish.common.util;

import org.apache.commons.lang3.StringUtils;

/**
 * 通用值转换工具。
 */
public final class ValueSupport {

    private ValueSupport() {
    }

    public static String asString(Object value) {
        return value instanceof String text ? text : null;
    }

    public static long asLong(Object value, long fallback) {
        if (value instanceof Number number) {
            return number.longValue();
        }
        if (value instanceof String text && StringUtils.isNotBlank(text)) {
            try {
                return Long.parseLong(text);
            } catch (NumberFormatException ignored) {
                return fallback;
            }
        }
        return fallback;
    }
}
