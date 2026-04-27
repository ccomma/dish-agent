package com.example.dish.memory.storage;

import com.example.dish.control.memory.model.MemoryLayer;
import com.example.dish.control.memory.model.MemoryWriteRequest;
import com.example.dish.memory.model.LongTermMemoryDocument;
import com.example.dish.memory.model.LongTermMemoryHit;
import dev.langchain4j.data.document.Metadata;
import dev.langchain4j.data.embedding.Embedding;
import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.model.embedding.EmbeddingModel;
import dev.langchain4j.store.embedding.EmbeddingSearchRequest;
import dev.langchain4j.store.embedding.EmbeddingStore;
import dev.langchain4j.store.embedding.filter.Filter;
import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;

import static dev.langchain4j.store.embedding.filter.MetadataFilterBuilder.metadataKey;

/**
 * 长期记忆向量存储。
 * 这里统一封装长期知识的文档落库、向量检索、Milvus/InMemory 切换和故障回放，
 * 让上层服务只关心“写入长期知识”和“按语义召回长期知识”两个动作。
 */
@Service
public class LongTermMemoryVectorStore {

    private static final Logger log = LoggerFactory.getLogger(LongTermMemoryVectorStore.class);
    private static final String GLOBAL_TENANT = "GLOBAL";

    private final Map<String, LongTermMemoryDocument> catalog = new ConcurrentHashMap<>();
    private final AtomicBoolean initialized = new AtomicBoolean(false);
    private final LongTermMemoryDocumentAssembler documentAssembler = new LongTermMemoryDocumentAssembler();
    private final EmbeddingModelFactory embeddingModelFactory = new EmbeddingModelFactory();
    private final LongTermEmbeddingStoreFactory embeddingStoreFactory = new LongTermEmbeddingStoreFactory();

    @Value("${memory.retrieval.vector-dim:128}")
    private int vectorDim = 128;

    @Value("${memory.long-term.provider:inmemory}")
    private String provider = "inmemory";

    @Value("${memory.long-term.embedding-provider:hash}")
    private String embeddingProvider = "hash";

    @Value("${memory.long-term.min-score:0.12}")
    private double minScore = 0.12;

    @Value("${memory.long-term.milvus.host:${MILVUS_HOST:localhost}}")
    private String milvusHost = "localhost";

    @Value("${memory.long-term.milvus.port:${MILVUS_PORT:19530}}")
    private int milvusPort = 19530;

    @Value("${memory.long-term.milvus.collection:dish_memory_long_term}")
    private String milvusCollection = "dish_memory_long_term";

    @Value("${memory.long-term.openai.api-key:${OPENAI_API_KEY:}}")
    private String openAiApiKey = "";

    @Value("${memory.long-term.openai.base-url:${OPENAI_BASE_URL:https://api.openai.com/v1}}")
    private String openAiBaseUrl = "https://api.openai.com/v1";

    @Value("${memory.long-term.openai.model:text-embedding-3-small}")
    private String openAiModel = "text-embedding-3-small";

    private volatile EmbeddingStore<TextSegment> embeddingStore;
    private volatile EmbeddingModel embeddingModel;

    public void index(MemoryWriteRequest request) {
        // 1. 只处理长期知识层写入，其他记忆层由时间线存储负责。
        if (request == null || request.memoryLayer() != MemoryLayer.LONG_TERM_KNOWLEDGE) {
            return;
        }

        // 2. 确保 embedding 模型和向量库已经初始化。
        ensureReady();
        LongTermMemoryDocument document = documentAssembler.toDocument(request);
        catalog.put(document.id(), document);

        try {
            // 3. 将文档内容和元数据写入当前向量库。
            addDocumentToStore(document);
        } catch (Exception ex) {
            // 4. 外部向量库失败时回退到本地内存向量库，保证演示链路可用。
            log.warn("long-term memory index failed, fallback to in-memory store: id={}, provider={}, message={}",
                    document.id(), provider, ex.getMessage());
            fallbackToInMemoryStore();
            addDocumentToStore(document);
        }
    }

    public List<LongTermMemoryHit> search(String tenantId, String query, int limit) {
        // 1. 空查询不触发向量检索。
        if (StringUtils.isBlank(query)) {
            return List.of();
        }

        // 2. 构造带租户过滤的向量检索请求。
        ensureReady();
        try {
            // 3. 将向量库命中转换为长期记忆召回结果。
            return searchHits(tenantId, query, limit);
        } catch (Exception ex) {
            // 4. 检索失败时切回本地向量库并重试一次当前查询。
            log.warn("long-term memory search failed: provider={}, tenantId={}, message={}",
                    provider, tenantId, ex.getMessage());
            fallbackToInMemoryStore();
            return search(tenantId, query, limit);
        }
    }

    public String source() {
        ensureReady();
        if ("milvus".equalsIgnoreCase(provider)) {
            return "milvus:" + milvusCollection;
        }
        return "inmemory-long-term";
    }

    public void clearForTest() {
        catalog.clear();
        embeddingStore = null;
        embeddingModel = null;
        initialized.set(false);
    }

    private Filter buildFilter(String tenantId) {
        // 1. 长期库固定只检索 LONG_TERM_KNOWLEDGE，避免混入短期时间线数据。
        Filter layer = metadataKey("memoryLayer").isEqualTo(MemoryLayer.LONG_TERM_KNOWLEDGE.name());
        if (StringUtils.isBlank(tenantId)) {
            return layer;
        }
        // 2. 有租户上下文时，同时放行当前租户和 GLOBAL 预热知识。
        Filter tenant = metadataKey("tenantId").isEqualTo(tenantId)
                .or(metadataKey("tenantId").isEqualTo(GLOBAL_TENANT));
        return layer.and(tenant);
    }

    private void ensureReady() {
        if (initialized.compareAndSet(false, true)) {
            // 1. 首次使用时按配置初始化 embedding 模型和底层向量库。
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
            // 2. 再把内存 catalog 回放到当前 store，保证切换 provider 后仍能查到已写入知识。
            replayCatalog();
        }
    }

    private void fallbackToInMemoryStore() {
        // 1. 故障时强制切到本地内存向量库，避免反复打爆外部依赖。
        provider = "inmemory";
        embeddingStore = embeddingStoreFactory.createInMemory();
        // 2. 把已有 catalog 全量回放到 fallback store，保证当前进程内已经写入的知识仍可检索。
        replayCatalog();
    }

    private void addDocumentToStore(LongTermMemoryDocument document) {
        Embedding embedding = embeddingModel.embed(document.content()).content();
        TextSegment segment = TextSegment.from(document.content(), Metadata.from(document.metadata()));
        embeddingStore.addAll(List.of(document.id()), List.of(embedding), List.of(segment));
    }

    private List<LongTermMemoryHit> searchHits(String tenantId, String query, int limit) {
        EmbeddingSearchRequest request = EmbeddingSearchRequest.builder()
                .queryEmbedding(embeddingModel.embed(query).content())
                .maxResults(Math.max(limit, 5))
                .minScore(minScore)
                .filter(buildFilter(tenantId))
                .build();
        return embeddingStore.search(request).matches().stream()
                .map(match -> documentAssembler.toHit(match, catalog, source()))
                .filter(Objects::nonNull)
                .limit(Math.max(limit, 5))
                .toList();
    }

    private void replayCatalog() {
        if (embeddingStore == null || embeddingModel == null || catalog.isEmpty()) {
            return;
        }
        // 1. 先把 catalog 中的文档重新计算 embedding，构造一批待回放向量。
        List<String> ids = new ArrayList<>();
        List<Embedding> embeddings = new ArrayList<>();
        List<TextSegment> segments = new ArrayList<>();
        for (LongTermMemoryDocument document : catalog.values()) {
            ids.add(document.id());
            embeddings.add(embeddingModel.embed(document.content()).content());
            segments.add(TextSegment.from(document.content(), Metadata.from(document.metadata())));
        }
        // 2. 一次性批量写回当前 store，减少 provider 切换时的重复调用成本。
        embeddingStore.addAll(ids, embeddings, segments);
    }

}
