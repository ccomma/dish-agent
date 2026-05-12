package com.example.dish.memory.storage;

import org.springframework.stereotype.Component;

import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.model.embedding.EmbeddingModel;
import dev.langchain4j.store.embedding.EmbeddingStore;

/**
 * 长期记忆 provider 运行态。
 * 负责 embedding 模型、底层向量库和 fallback provider 的生命周期切换。
 */
@Component
public class LongTermMemoryProviderRuntime {

    private final EmbeddingModelFactory embeddingModelFactory = new EmbeddingModelFactory();
    private final LongTermEmbeddingStoreFactory embeddingStoreFactory = new LongTermEmbeddingStoreFactory();

    private volatile String activeProvider = "inmemory";
    private volatile EmbeddingStore<TextSegment> embeddingStore;
    private volatile EmbeddingModel embeddingModel;

    void initialize(String provider,
                    String embeddingProvider,
                    String openAiApiKey,
                    String openAiBaseUrl,
                    String openAiModel,
                    int vectorDim,
                    String milvusHost,
                    int milvusPort,
                    String milvusCollection) {
        // 1. 首次使用时按配置创建模型和向量库；后续 fallback 只更新 activeProvider。
        activeProvider = provider;
        embeddingModel = embeddingModelFactory.create(
                embeddingProvider,
                openAiApiKey,
                openAiBaseUrl,
                openAiModel,
                vectorDim
        );
        embeddingStore = embeddingStoreFactory.create(
                provider,
                milvusHost,
                milvusPort,
                milvusCollection,
                vectorDim
        );
    }

    void fallbackToInMemoryStore() {
        // 1. 外部 provider 故障后强制切回本地内存库，避免持续访问不可用依赖。
        activeProvider = "inmemory";
        embeddingStore = embeddingStoreFactory.createInMemory();
    }

    boolean ready() {
        return embeddingStore != null && embeddingModel != null;
    }

    EmbeddingStore<TextSegment> embeddingStore() {
        return embeddingStore;
    }

    EmbeddingModel embeddingModel() {
        return embeddingModel;
    }

    String source(String milvusCollection) {
        if ("milvus".equalsIgnoreCase(activeProvider)) {
            return "milvus:" + milvusCollection;
        }
        return "inmemory-long-term";
    }

    void clear() {
        activeProvider = "inmemory";
        embeddingStore = null;
        embeddingModel = null;
    }
}
