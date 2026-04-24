package com.example.dish.memory.retrieval;

import com.example.dish.memory.model.MemoryEntry;

/**
 * 召回阶段内部使用的候选对象，聚合 entry 和各段打分结果。
 */
record MemoryRetrievalCandidate(MemoryEntry entry,
                                double totalScore,
                                double keywordScore,
                                double vectorScore,
                                double recencyScore,
                                String explanation) {
}
