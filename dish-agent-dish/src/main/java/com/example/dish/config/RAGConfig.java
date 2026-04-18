package com.example.dish.config;

import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.model.cohere.CohereScoringModel;
import dev.langchain4j.model.embedding.EmbeddingModel;
import dev.langchain4j.model.openai.OpenAiChatModel;
import dev.langchain4j.model.openai.OpenAiEmbeddingModel;
import dev.langchain4j.model.scoring.ScoringModel;
import dev.langchain4j.store.embedding.EmbeddingStore;
import dev.langchain4j.store.embedding.inmemory.InMemoryEmbeddingStore;
import dev.langchain4j.store.embedding.milvus.MilvusEmbeddingStore;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * RAG 配置类
 *
 * 统一管理 EmbeddingModel、EmbeddingStore、ScoringModel、ChatModel 等 Bean
 */
@Configuration
public class RAGConfig {

    @Value("${llm.api-key}")
    private String apiKey;

    @Value("${llm.base-url}")
    private String baseUrl;

    @Value("${llm.model}")
    private String chatModelName;

    @Value("${rag.embedding.model}")
    private String embeddingModelName;

    @Value("${rag.embedding.dimension}")
    private int embeddingDimension;

    @Value("${rag.vector-store-type}")
    private String vectorStoreType;

    @Value("${rag.milvus.host}")
    private String milvusHost;

    @Value("${rag.milvus.port}")
    private int milvusPort;

    @Value("${rag.milvus.collection}")
    private String milvusCollection;

    @Value("${rag.cohere.api-key:#{null}}")
    private String cohereApiKey;

    /**
     * Embedding 模型 Bean
     */
    @Bean
    public EmbeddingModel embeddingModel() {
        return OpenAiEmbeddingModel.builder()
                .apiKey(apiKey)
                .baseUrl(baseUrl)
                .modelName(embeddingModelName)
                .build();
    }

    /**
     * 向量存储 Bean
     */
    @Bean
    public EmbeddingStore<TextSegment> embeddingStore() {
        if ("milvus".equalsIgnoreCase(vectorStoreType)) {
            return MilvusEmbeddingStore.builder()
                    .host(milvusHost)
                    .port(milvusPort)
                    .collectionName(milvusCollection)
                    .dimension(embeddingDimension)
                    .build();
        }
        return new InMemoryEmbeddingStore<>();
    }

    /**
     * 重排序模型 Bean（可选）
     */
    @Bean
    public ScoringModel scoringModel() {
        if (cohereApiKey != null && !cohereApiKey.isEmpty()
                && !"your_cohere_api_key_here".equals(cohereApiKey)) {
            return CohereScoringModel.builder()
                    .apiKey(cohereApiKey)
                    .build();
        }
        return null;
    }

    /**
     * 聊天模型 Bean
     */
    @Bean
    public ChatModel chatModel() {
        return OpenAiChatModel.builder()
                .apiKey(apiKey)
                .baseUrl(baseUrl)
                .modelName(chatModelName)
                .temperature(0.3)
                .build();
    }
}
