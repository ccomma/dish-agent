package com.example.dish.control.memory.model;

import java.io.Serializable;
import java.util.List;

public record MemoryTimelineResult(
        List<MemoryTimelineEntry> entries,
        String source,
        int total
) implements Serializable {
}
