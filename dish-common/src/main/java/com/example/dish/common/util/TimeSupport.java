package com.example.dish.common.util;

import java.time.Instant;

/**
 * 时间转换工具。
 */
public final class TimeSupport {

    private TimeSupport() {
    }

    public static long durationMs(Instant startedAt, Instant endedAt) {
        if (startedAt == null || endedAt == null) {
            return 0L;
        }
        return Math.max(0L, endedAt.toEpochMilli() - startedAt.toEpochMilli());
    }
}
