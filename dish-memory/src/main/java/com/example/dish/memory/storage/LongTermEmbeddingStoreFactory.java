package com.example.dish.memory.storage;

import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.store.embedding.EmbeddingStore;
import dev.langchain4j.store.embedding.inmemory.InMemoryEmbeddingStore;
import dev.langchain4j.store.embedding.milvus.MilvusEmbeddingStore;
import io.milvus.param.MetricType;

/**
 * 长期记忆底层向量库工厂。
 * 负责根据 provider 配置创建 Milvus 或本地内存 store，主存储类只保留 provider 切换和回放编排。
 */
public class LongTermEmbeddingStoreFactory {

    public EmbeddingStore<TextSegment> create(
            String provider,
            String milvusHost,
            int milvusPort,
            String milvusCollection,
            int vectorDim
    ) {
        if ("milvus".equalsIgnoreCase(provider)) {
            return MilvusEmbeddingStore.builder()
                    .host(milvusHost)
                    .port(milvusPort)
                    .collectionName(milvusCollection)
                    .dimension(vectorDim)
                    .metricType(MetricType.COSINE)
                    .build();
        }
        return createInMemory();
    }

    public EmbeddingStore<TextSegment> createInMemory() {
        return new InMemoryEmbeddingStore<>();
    }
}
