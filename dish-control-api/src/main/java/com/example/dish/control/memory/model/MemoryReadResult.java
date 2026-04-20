package com.example.dish.control.memory.model;

import java.io.Serializable;
import java.util.List;

public record MemoryReadResult(
        List<String> snippets,
        String source,
        boolean hit
) implements Serializable {
}
