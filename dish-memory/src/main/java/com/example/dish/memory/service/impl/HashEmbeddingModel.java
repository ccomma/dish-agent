package com.example.dish.memory.service.impl;

import dev.langchain4j.data.embedding.Embedding;
import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.model.embedding.EmbeddingModel;
import dev.langchain4j.model.output.Response;

import java.util.List;

final class HashEmbeddingModel implements EmbeddingModel {

    private final int dimension;

    HashEmbeddingModel(int dimension) {
        this.dimension = Math.max(dimension, 32);
    }

    @Override
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
