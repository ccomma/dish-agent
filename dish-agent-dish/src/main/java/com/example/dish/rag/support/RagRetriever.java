package com.example.dish.rag.support;

import com.example.dish.service.EmbeddingService;
import dev.langchain4j.data.embedding.Embedding;
import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.model.output.Response;
import dev.langchain4j.model.scoring.ScoringModel;
import dev.langchain4j.store.embedding.EmbeddingMatch;
import dev.langchain4j.store.embedding.EmbeddingSearchRequest;
import dev.langchain4j.store.embedding.EmbeddingSearchResult;
import dev.langchain4j.store.embedding.EmbeddingStore;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * RAG 检索器。
 * 负责 embedding 检索、可选重排和上下文拼装，让主管道类只保留“检索后生成”的主流程编排。
 */
@Component
public class RagRetriever {

    @Autowired
    private EmbeddingService embeddingService;
    @Autowired
    private EmbeddingStore<TextSegment> embeddingStore;
    @Autowired(required = false)
    private ScoringModel scoringModel;

    public String retrieveContext(String query, int topK) {
        // 1. 先用 embedding 检索拿到初始候选。
        Embedding queryEmbedding = embeddingService.embed(query);
        EmbeddingSearchResult<TextSegment> initialResult = embeddingStore.search(
                EmbeddingSearchRequest.builder()
                        .queryEmbedding(queryEmbedding)
                        .maxResults(5)
                        .build()
        );

        // 2. 有重排模型时再做 rerank，得到最终上下文片段。
        List<TextSegment> segments = initialResult.matches().stream()
                .map(EmbeddingMatch::embedded)
                .toList();
        List<TextSegment> finalSegments = rerankSegments(segments, query, topK);

        // 3. 把命中的知识片段拼成统一上下文，供生成阶段引用。
        return assembleContext(finalSegments);
    }

    private List<TextSegment> rerankSegments(List<TextSegment> segments, String query, int topK) {
        if (segments.isEmpty()) {
            return List.of();
        }
        if (scoringModel == null) {
            return segments.stream().limit(topK).toList();
        }

        Response<List<Double>> scores = scoringModel.scoreAll(segments, query);
        List<Double> scoreList = scores.content();
        List<Map.Entry<Double, TextSegment>> scored = new ArrayList<>();
        for (int i = 0; i < segments.size() && i < scoreList.size(); i++) {
            scored.add(Map.entry(scoreList.get(i), segments.get(i)));
        }
        scored.sort((a, b) -> Double.compare(b.getKey(), a.getKey()));
        return scored.stream().limit(topK).map(Map.Entry::getValue).toList();
    }

    private String assembleContext(List<TextSegment> finalSegments) {
        StringBuilder context = new StringBuilder();
        context.append("【参考信息】\n\n");
        if (finalSegments.isEmpty()) {
            context.append("未找到直接相关的参考信息。\n");
            return context.toString();
        }

        for (TextSegment segment : finalSegments) {
            context.append("─".repeat(40)).append('\n');
            context.append("【").append(extractName(segment.text())).append("】\n");
            context.append(segment.text()).append("\n\n");
        }
        return context.toString();
    }

    private String extractName(String text) {
        if (text.startsWith("【") && text.contains("】")) {
            return text.substring(1, text.indexOf("】"));
        }
        return "知识片段";
    }
}
