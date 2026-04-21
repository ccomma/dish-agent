package com.example.dish.memory.service.impl;

import com.example.dish.control.memory.model.MemoryLayer;
import com.example.dish.control.memory.model.MemoryWriteRequest;
import dev.langchain4j.data.document.Metadata;
import dev.langchain4j.data.embedding.Embedding;
import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.model.embedding.EmbeddingModel;
import dev.langchain4j.model.openai.OpenAiEmbeddingModel;
import dev.langchain4j.store.embedding.EmbeddingMatch;
import dev.langchain4j.store.embedding.EmbeddingSearchRequest;
import dev.langchain4j.store.embedding.EmbeddingStore;
import dev.langchain4j.store.embedding.filter.Filter;
import dev.langchain4j.store.embedding.inmemory.InMemoryEmbeddingStore;
import dev.langchain4j.store.embedding.milvus.MilvusEmbeddingStore;
import io.milvus.param.MetricType;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;

import static dev.langchain4j.store.embedding.filter.MetadataFilterBuilder.metadataKey;

@Service
public class LongTermMemoryVectorStore {

    private static final Logger log = LoggerFactory.getLogger(LongTermMemoryVectorStore.class);
    private static final String GLOBAL_TENANT = "GLOBAL";

    private final Map<String, StoredVectorDocument> catalog = new ConcurrentHashMap<>();
    private final AtomicBoolean initialized = new AtomicBoolean(false);

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
        if (request == null || request.memoryLayer() != MemoryLayer.LONG_TERM_KNOWLEDGE) {
            return;
        }

        ensureReady();
        StoredVectorDocument document = toDocument(request);
        catalog.put(document.id(), document);

        try {
            Embedding embedding = embeddingModel.embed(document.content()).content();
            TextSegment segment = TextSegment.from(document.content(), Metadata.from(document.metadata()));
            embeddingStore.addAll(List.of(document.id()), List.of(embedding), List.of(segment));
        } catch (Exception ex) {
            log.warn("long-term memory index failed, fallback to in-memory store: id={}, provider={}, message={}",
                    document.id(), provider, ex.getMessage());
            fallbackToInMemoryStore();
            Embedding embedding = embeddingModel.embed(document.content()).content();
            TextSegment segment = TextSegment.from(document.content(), Metadata.from(document.metadata()));
            embeddingStore.addAll(List.of(document.id()), List.of(embedding), List.of(segment));
        }
    }

    public List<VectorHit> search(String tenantId, String query, int limit) {
        if (blank(query)) {
            return List.of();
        }

        ensureReady();
        try {
            EmbeddingSearchRequest request = EmbeddingSearchRequest.builder()
                    .queryEmbedding(embeddingModel.embed(query).content())
                    .maxResults(Math.max(limit, 5))
                    .minScore(minScore)
                    .filter(buildFilter(tenantId))
                    .build();

            return embeddingStore.search(request).matches().stream()
                    .map(this::toHit)
                    .filter(Objects::nonNull)
                    .limit(Math.max(limit, 5))
                    .toList();
        } catch (Exception ex) {
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

    void clearForTest() {
        catalog.clear();
        embeddingStore = null;
        embeddingModel = null;
        initialized.set(false);
    }

    private Filter buildFilter(String tenantId) {
        Filter layer = metadataKey("memoryLayer").isEqualTo(MemoryLayer.LONG_TERM_KNOWLEDGE.name());
        if (blank(tenantId)) {
            return layer;
        }
        Filter tenant = metadataKey("tenantId").isEqualTo(tenantId)
                .or(metadataKey("tenantId").isEqualTo(GLOBAL_TENANT));
        return layer.and(tenant);
    }

    private VectorHit toHit(EmbeddingMatch<TextSegment> match) {
        if (match == null || match.embedded() == null) {
            return null;
        }

        TextSegment segment = match.embedded();
        Metadata metadata = segment.metadata();
        String documentId = match.embeddingId();
        StoredVectorDocument document = documentId != null ? catalog.get(documentId) : null;
        Map<String, Object> documentMetadata = document != null ? document.metadata() : metadata.toMap();

        return new VectorHit(
                documentId,
                metadata.getString("memoryType"),
                MemoryLayer.LONG_TERM_KNOWLEDGE,
                segment.text(),
                documentMetadata,
                metadata.getString("traceId"),
                parseInstant(metadata.getString("createdAt")),
                metadata.getLong("sequence") != null ? metadata.getLong("sequence") : 0L,
                source(),
                match.score() != null ? match.score() : 0.0
        );
    }

    private StoredVectorDocument toDocument(MemoryWriteRequest request) {
        Instant now = Instant.now();
        long sequence = now.toEpochMilli();
        Map<String, Object> metadata = new LinkedHashMap<>();
        if (request.metadata() != null) {
            metadata.putAll(request.metadata());
        }
        metadata.put("tenantId", blank(request.tenantId()) ? GLOBAL_TENANT : request.tenantId());
        metadata.put("sessionId", request.sessionId());
        metadata.put("memoryType", request.memoryType());
        metadata.put("memoryLayer", request.memoryLayer().name());
        metadata.put("traceId", request.traceId());
        metadata.put("createdAt", now.toString());
        metadata.put("sequence", sequence);

        String id = UUID.nameUUIDFromBytes((
                metadata.get("tenantId") + "::" +
                        request.sessionId() + "::" +
                        request.memoryType() + "::" +
                        request.content()
        ).getBytes(StandardCharsets.UTF_8)).toString();

        return new StoredVectorDocument(
                id,
                String.valueOf(metadata.get("tenantId")),
                request.sessionId(),
                request.memoryType(),
                request.memoryLayer(),
                request.content(),
                Map.copyOf(metadata),
                request.traceId(),
                now,
                sequence
        );
    }

    private void ensureReady() {
        if (initialized.compareAndSet(false, true)) {
            embeddingModel = buildEmbeddingModel();
            embeddingStore = buildStore();
            replayCatalog();
        }
    }

    private EmbeddingModel buildEmbeddingModel() {
        if ("openai".equalsIgnoreCase(embeddingProvider) && !blank(openAiApiKey)) {
            return OpenAiEmbeddingModel.builder()
                    .apiKey(openAiApiKey)
                    .baseUrl(openAiBaseUrl)
                    .modelName(openAiModel)
                    .build();
        }
        return new HashEmbeddingModel(vectorDim);
    }

    private EmbeddingStore<TextSegment> buildStore() {
        if ("milvus".equalsIgnoreCase(provider)) {
            return MilvusEmbeddingStore.builder()
                    .host(milvusHost)
                    .port(milvusPort)
                    .collectionName(milvusCollection)
                    .dimension(vectorDim)
                    .metricType(MetricType.COSINE)
                    .build();
        }
        return new InMemoryEmbeddingStore<>();
    }

    private void fallbackToInMemoryStore() {
        provider = "inmemory";
        embeddingStore = new InMemoryEmbeddingStore<>();
        replayCatalog();
    }

    private void replayCatalog() {
        if (embeddingStore == null || embeddingModel == null || catalog.isEmpty()) {
            return;
        }
        List<String> ids = new ArrayList<>();
        List<Embedding> embeddings = new ArrayList<>();
        List<TextSegment> segments = new ArrayList<>();
        for (StoredVectorDocument document : catalog.values()) {
            ids.add(document.id());
            embeddings.add(embeddingModel.embed(document.content()).content());
            segments.add(TextSegment.from(document.content(), Metadata.from(document.metadata())));
        }
        embeddingStore.addAll(ids, embeddings, segments);
    }

    private static Instant parseInstant(String value) {
        if (blank(value)) {
            return Instant.now();
        }
        return Instant.parse(value);
    }

    private static boolean blank(String value) {
        return value == null || value.isBlank();
    }

    private record StoredVectorDocument(String id,
                                        String tenantId,
                                        String sessionId,
                                        String memoryType,
                                        MemoryLayer memoryLayer,
                                        String content,
                                        Map<String, Object> metadata,
                                        String traceId,
                                        Instant createdAt,
                                        long sequence) {
    }

    public record VectorHit(String entryId,
                            String memoryType,
                            MemoryLayer memoryLayer,
                            String content,
                            Map<String, Object> metadata,
                            String traceId,
                            Instant createdAt,
                            long sequence,
                            String retrievalSource,
                            double vectorScore) {
    }
}
