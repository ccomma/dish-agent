package com.example.dish.memory.support;

import dev.langchain4j.data.embedding.Embedding;
import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.model.embedding.EmbeddingModel;
import dev.langchain4j.model.output.Response;

import java.util.List;

/**
 * 基于本地 hash 向量算法实现的 EmbeddingModel。
 */
public final class HashEmbeddingModel implements EmbeddingModel {

    private final int dimension;

    public HashEmbeddingModel(int dimension) {
        this.dimension = Math.max(dimension, 32);
    }

    @Override
    /**
     * 使用本地 hash 向量器批量生成 embedding，作为外部 embedding provider 不可用时的 fallback。
     */
    public Response<List<Embedding>> embedAll(List<TextSegment> textSegments) {
        List<Embedding> embeddings = textSegments.stream()
                .map(segment -> Embedding.from(MemoryVectorSupport.toFloatVector(MemoryVectorSupport.embed(segment.text(), dimension))))
                .toList();
        return Response.from(embeddings);
    }

    @Override
    public int dimension() {
        return dimension;
    }

    @Override
    public String modelName() {
        return "hash-embedding";
    }
}
