package com.example.dish.service;

import dev.langchain4j.data.embedding.Embedding;
import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.model.embedding.EmbeddingModel;
import org.springframework.stereotype.Component;

import javax.annotation.Resource;

/**
 * Embedding 服务
 * 负责文本向量化和管理 EmbeddingModel 生命周期
 */
@Component
public class EmbeddingService {

    @Resource
    private EmbeddingModel embeddingModel;

    /**
     * 将文本转换为向量
     */
    public Embedding embed(String text) {
        return embeddingModel.embed(text).content();
    }

    /**
     * 将文本转换为 TextSegment
     */
    public TextSegment embedAsSegment(String text) {
        return TextSegment.from(text);
    }

    /**
     * 获取 EmbeddingModel 实例
     */
    public EmbeddingModel getModel() {
        return embeddingModel;
    }
}
