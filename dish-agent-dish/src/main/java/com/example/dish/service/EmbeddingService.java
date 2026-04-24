package com.example.dish.service;

import dev.langchain4j.data.embedding.Embedding;
import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.model.embedding.EmbeddingModel;
import org.springframework.stereotype.Component;

import javax.annotation.Resource;

/**
 * Embedding 服务。
 * 屏蔽底层 EmbeddingModel 细节，给 RAG 管道提供统一的文本向量化入口。
 */
@Component
public class EmbeddingService {

    @Resource
    private EmbeddingModel embeddingModel;

    public Embedding embed(String text) {
        return embeddingModel.embed(text).content();
    }

    public TextSegment embedAsSegment(String text) {
        return TextSegment.from(text);
    }

    public EmbeddingModel getModel() {
        return embeddingModel;
    }
}
