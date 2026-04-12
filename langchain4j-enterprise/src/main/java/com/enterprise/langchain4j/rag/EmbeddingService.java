package com.enterprise.langchain4j.rag;

import com.enterprise.langchain4j.Config;
import dev.langchain4j.data.embedding.Embedding;
import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.model.embedding.EmbeddingModel;
import dev.langchain4j.model.openai.OpenAiEmbeddingModel;

/**
 * Embedding 服务
 * 负责文本向量化和管理 EmbeddingModel 生命周期
 */
public class EmbeddingService {

    private final EmbeddingModel embeddingModel;

    public EmbeddingService() {
        Config config = Config.getInstance();
        this.embeddingModel = OpenAiEmbeddingModel.builder()
                .apiKey(config.getApiKey())
                .baseUrl(config.getBaseUrl())
                .modelName(config.getEmbeddingModel())
                .build();
    }

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
