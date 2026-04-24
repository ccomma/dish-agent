package com.example.dish.memory.retrieval;

import com.example.dish.control.memory.model.MemoryLayer;
import com.example.dish.control.memory.model.MemoryReadResult;
import com.example.dish.control.memory.model.MemoryRetrievalHit;
import org.springframework.stereotype.Component;

import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;

/**
 * 记忆召回结果组装器。
 *
 * <p>该类只负责把内部候选结果转换成 control-api 需要的返回结构，
 * 让召回编排器本身不再同时承担排序、解释和 DTO 拼装三种职责。</p>
 */
@Component
public class MemoryRetrievalResultAssembler {

    public MemoryReadResult assemble(List<MemoryRetrievalCandidate> candidates, int limit) {
        // 1. 按总分降序、sequence 降序对候选排序，并截断到请求限制。
        List<MemoryRetrievalHit> hits = candidates.stream()
                .sorted(Comparator.comparingDouble(MemoryRetrievalCandidate::totalScore).reversed()
                        .thenComparing(candidate -> candidate.entry().sequence(), Comparator.reverseOrder()))
                .limit(limit)
                .map(this::toHit)
                .toList();

        // 2. 提取给 Agent Prompt 使用的纯内容片段列表。
        List<String> snippets = hits.stream()
                .map(MemoryRetrievalHit::content)
                .toList();

        // 3. 汇总本次命中的 retrieval source，供控制台解释召回来源。
        String source = hits.stream()
                .map(MemoryRetrievalHit::retrievalSource)
                .filter(Objects::nonNull)
                .collect(java.util.stream.Collectors.collectingAndThen(
                        java.util.stream.Collectors.toCollection(LinkedHashSet::new),
                        set -> set.isEmpty() ? "none" : String.join(" + ", set)
                ));

        return new MemoryReadResult(snippets, source, !hits.isEmpty(), hits);
    }

    public String explain(String source, double keywordScore, double vectorScore, double recencyScore, MemoryLayer layer) {
        // 解释文本保持稳定格式，方便控制台直接展示与排障时人工阅读。
        return "layer=" + layer.name()
                + ", source=" + source
                + ", keyword=" + String.format("%.3f", keywordScore)
                + ", vector=" + String.format("%.3f", vectorScore)
                + ", recency=" + String.format("%.3f", recencyScore);
    }

    private MemoryRetrievalHit toHit(MemoryRetrievalCandidate candidate) {
        return new MemoryRetrievalHit(
                candidate.entry().memoryType(),
                candidate.entry().memoryLayer(),
                candidate.entry().content(),
                candidate.entry().metadata(),
                candidate.entry().traceId(),
                candidate.entry().createdAt(),
                candidate.entry().sequence(),
                candidate.entry().storageSource(),
                candidate.totalScore(),
                candidate.keywordScore(),
                candidate.vectorScore(),
                candidate.recencyScore(),
                candidate.explanation()
        );
    }
}
