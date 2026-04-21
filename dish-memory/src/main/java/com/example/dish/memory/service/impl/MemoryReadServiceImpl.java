package com.example.dish.memory.service.impl;

import com.example.dish.common.telemetry.DubboOpenTelemetrySupport;
import com.example.dish.control.memory.model.MemoryLayer;
import com.example.dish.control.memory.model.MemoryReadRequest;
import com.example.dish.control.memory.model.MemoryReadResult;
import com.example.dish.control.memory.model.MemoryRetrievalHit;
import com.example.dish.control.memory.model.MemoryTimelineRequest;
import com.example.dish.control.memory.service.MemoryReadService;
import org.apache.dubbo.config.annotation.DubboService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicLong;

@Service
@DubboService(interfaceClass = MemoryReadService.class, timeout = 5000, retries = 0)
public class MemoryReadServiceImpl implements MemoryReadService {

    private static final int DEFAULT_LIMIT = 5;
    private static final int REDIS_FETCH_MULTIPLIER = 20;
    private static final Map<String, CopyOnWriteArrayList<MemoryEntry>> STORE = new ConcurrentHashMap<>();
    private static final AtomicLong SEQ = new AtomicLong(0);

    @Value("${memory.mode:bootstrap}")
    private String memoryMode = "bootstrap";

    @Value("${memory.retrieval.vector-dim:128}")
    private int vectorDim = 128;

    @Value("${memory.retrieval.candidate-fetch-size:200}")
    private int candidateFetchSize = 200;

    @Value("${memory.retrieval.keyword-weight:0.45}")
    private double keywordWeight = 0.45;

    @Value("${memory.retrieval.vector-weight:0.55}")
    private double vectorWeight = 0.55;

    @Autowired(required = false)
    private StringRedisTemplate redisTemplate;

    @Autowired(required = false)
    private LongTermMemoryVectorStore longTermMemoryVectorStore;

    @Override
    public MemoryReadResult read(MemoryReadRequest request) {
        DubboOpenTelemetrySupport.RpcSpanScope spanScope =
                DubboOpenTelemetrySupport.openProviderSpan("memory.read", "dish-memory");
        try (spanScope) {
            if (request == null || isBlank(request.tenantId()) || isBlank(request.sessionId())) {
                return new MemoryReadResult(List.of(), "none", false, List.of());
            }

            int limit = request.limit() > 0 ? request.limit() : DEFAULT_LIMIT;
            List<MemoryLayer> layers = normalizedLayers(request.memoryLayers());
            String query = request.query();
            double[] queryVector = isBlank(query) ? new double[0] : MemoryVectorSupport.embed(query, vectorDim);

            List<RetrievalCandidate> candidates = new ArrayList<>();
            candidates.addAll(retrieveOperationalMemory(request, layers, limit, query, queryVector));
            candidates.addAll(retrieveLongTermMemory(request, layers, limit, query));

            List<MemoryRetrievalHit> hits = candidates.stream()
                    .sorted(Comparator.comparingDouble(RetrievalCandidate::totalScore).reversed()
                            .thenComparing(candidate -> candidate.entry().sequence(), Comparator.reverseOrder()))
                    .limit(limit)
                    .map(this::toHit)
                    .toList();

            List<String> snippets = hits.stream()
                    .map(MemoryRetrievalHit::content)
                    .toList();

            String source = hits.stream()
                    .map(MemoryRetrievalHit::retrievalSource)
                    .filter(Objects::nonNull)
                    .collect(java.util.stream.Collectors.collectingAndThen(
                            java.util.stream.Collectors.toCollection(LinkedHashSet::new),
                            set -> set.isEmpty() ? "none" : String.join(" + ", set)
                    ));

            return new MemoryReadResult(snippets, source, !hits.isEmpty(), hits);
        } catch (RuntimeException ex) {
            spanScope.recordFailure(ex);
            throw ex;
        }
    }

    private List<RetrievalCandidate> retrieveOperationalMemory(MemoryReadRequest request,
                                                               List<MemoryLayer> layers,
                                                               int limit,
                                                               String query,
                                                               double[] queryVector) {
        List<MemoryLayer> operationalLayers = layers.stream()
                .filter(layer -> layer != MemoryLayer.LONG_TERM_KNOWLEDGE)
                .toList();
        if (operationalLayers.isEmpty()) {
            return List.of();
        }

        MemoryTimelineRequest timelineRequest = new MemoryTimelineRequest(
                request.tenantId(),
                request.sessionId(),
                null,
                null,
                null,
                Map.of(),
                Math.max(limit * REDIS_FETCH_MULTIPLIER, candidateFetchSize),
                request.traceId()
        );
        List<MemoryEntry> candidates = collectRetrievalCandidates(memoryMode, redisTemplate, timelineRequest, candidateFetchSize, operationalLayers);
        if (candidates.isEmpty()) {
            return List.of();
        }

        return candidates.stream()
                .map(entry -> {
                    double keywordScore = isBlank(query) ? 0.0 : scoreKeyword(query, entry);
                    double vectorScore = isBlank(query) ? 0.0 : semanticScore(entry, queryVector);
                    double recencyScore = recencyScore(entry);
                    double totalScore = isBlank(query)
                            ? recencyScore + 1.0
                            : keywordWeight * keywordScore + vectorWeight * vectorScore + recencyScore;
                    return new RetrievalCandidate(
                            entry,
                            totalScore,
                            keywordScore,
                            vectorScore,
                            recencyScore,
                            explain(entry.storageSource(), keywordScore, vectorScore, recencyScore, entry.memoryLayer())
                    );
                })
                .toList();
    }

    private List<RetrievalCandidate> retrieveLongTermMemory(MemoryReadRequest request,
                                                            List<MemoryLayer> layers,
                                                            int limit,
                                                            String query) {
        if (!layers.contains(MemoryLayer.LONG_TERM_KNOWLEDGE) || isBlank(query) || longTermMemoryVectorStore == null) {
            return List.of();
        }

        return longTermMemoryVectorStore.search(request.tenantId(), query, Math.max(limit * 2, DEFAULT_LIMIT)).stream()
                .map(hit -> {
                    MemoryEntry entry = new MemoryEntry(
                            hit.entryId(),
                            MemoryLayer.LONG_TERM_KNOWLEDGE,
                            hit.memoryType(),
                            hit.content(),
                            hit.metadata(),
                            hit.traceId(),
                            hit.createdAt(),
                            hit.sequence(),
                            hit.retrievalSource()
                    );
                    double keywordScore = scoreKeyword(query, entry);
                    double vectorScore = hit.vectorScore();
                    double recencyScore = recencyScore(entry);
                    return new RetrievalCandidate(
                            entry,
                            keywordWeight * keywordScore + vectorWeight * vectorScore + recencyScore,
                            keywordScore,
                            vectorScore,
                            recencyScore,
                            explain(hit.retrievalSource(), keywordScore, vectorScore, recencyScore, MemoryLayer.LONG_TERM_KNOWLEDGE)
                    );
                })
                .toList();
    }

    static void append(String memoryMode,
                       StringRedisTemplate redisTemplate,
                       int vectorDim,
                       String tenantId,
                       String sessionId,
                       MemoryLayer memoryLayer,
                       String memoryType,
                       String content,
                       Map<String, Object> metadata,
                       String traceId) {
        if (useRedis(memoryMode, redisTemplate)) {
            appendRedis(redisTemplate, vectorDim, tenantId, sessionId, memoryLayer, memoryType, content, metadata, traceId);
            return;
        }

        long sequence = SEQ.incrementAndGet();
        String key = key(tenantId, sessionId);
        STORE.computeIfAbsent(key, ignored -> new CopyOnWriteArrayList<>())
                .add(new MemoryEntry(
                        entryId(tenantId, sessionId, sequence),
                        memoryLayer,
                        memoryType,
                        content,
                        metadata == null ? Map.of() : Map.copyOf(metadata),
                        traceId,
                        Instant.now(),
                        sequence,
                        storageSource(memoryLayer, false)
                ));
    }

    static List<MemoryEntry> queryEntries(String memoryMode,
                                          StringRedisTemplate redisTemplate,
                                          MemoryTimelineRequest request) {
        if (request == null || isBlank(request.tenantId())) {
            return List.of();
        }

        if (useRedis(memoryMode, redisTemplate)) {
            return queryEntriesRedis(redisTemplate, request);
        }

        List<MemoryEntry> entries = collectEntries(request.tenantId(), request.sessionId());
        int limit = request.limit() > 0 ? request.limit() : DEFAULT_LIMIT;

        return entries.stream()
                .filter(entry -> request.memoryLayer() == null || request.memoryLayer() == entry.memoryLayer())
                .filter(entry -> isBlank(request.memoryType()) || request.memoryType().equals(entry.memoryType()))
                .filter(entry -> isBlank(request.keyword()) || entry.content().contains(request.keyword()))
                .filter(entry -> matchesMetadata(entry, request.metadataFilters()))
                .sorted(Comparator.comparingLong(MemoryEntry::sequence).reversed())
                .limit(limit)
                .toList();
    }

    static List<MemoryEntry> collectRetrievalCandidates(String memoryMode,
                                                        StringRedisTemplate redisTemplate,
                                                        MemoryTimelineRequest request,
                                                        int fetchSize,
                                                        List<MemoryLayer> allowedLayers) {
        if (request == null || isBlank(request.tenantId())) {
            return List.of();
        }
        if (useRedis(memoryMode, redisTemplate)) {
            return queryEntriesRedis(redisTemplate, request, fetchSize).stream()
                    .filter(entry -> allowedLayers == null || allowedLayers.isEmpty() || allowedLayers.contains(entry.memoryLayer()))
                    .toList();
        }
        List<MemoryEntry> entries = collectEntries(request.tenantId(), request.sessionId());
        return entries.stream()
                .filter(entry -> allowedLayers == null || allowedLayers.isEmpty() || allowedLayers.contains(entry.memoryLayer()))
                .sorted(Comparator.comparingLong(MemoryEntry::sequence).reversed())
                .limit(fetchSize)
                .toList();
    }

    private static List<MemoryEntry> collectEntries(String tenantId, String sessionId) {
        if (!isBlank(sessionId)) {
            return STORE.getOrDefault(key(tenantId, sessionId), new CopyOnWriteArrayList<>());
        }

        String prefix = tenantId + "::";
        return STORE.entrySet().stream()
                .filter(entry -> entry.getKey().startsWith(prefix))
                .flatMap(entry -> entry.getValue().stream())
                .toList();
    }

    static void clearForTest() {
        STORE.clear();
        SEQ.set(0);
    }

    static void saveApproval(String memoryMode,
                             StringRedisTemplate redisTemplate,
                             String tenantId,
                             String sessionId,
                             String approvalId,
                             String encodedTicket) {
        if (useRedis(memoryMode, redisTemplate)) {
            redisTemplate.opsForValue().set(redisApprovalKey(tenantId, sessionId, approvalId), encodedTicket);
        }
    }

    static String loadApproval(String memoryMode,
                               StringRedisTemplate redisTemplate,
                               String tenantId,
                               String sessionId,
                               String approvalId) {
        if (useRedis(memoryMode, redisTemplate)) {
            return redisTemplate.opsForValue().get(redisApprovalKey(tenantId, sessionId, approvalId));
        }
        return null;
    }

    static String source(String memoryMode, StringRedisTemplate redisTemplate) {
        return useRedis(memoryMode, redisTemplate) ? "redis" : "in-memory";
    }

    private static void appendRedis(StringRedisTemplate redisTemplate,
                                    int vectorDim,
                                    String tenantId,
                                    String sessionId,
                                    MemoryLayer memoryLayer,
                                    String memoryType,
                                    String content,
                                    Map<String, Object> metadata,
                                    String traceId) {
        long sequence = redisTemplate.opsForValue().increment(redisSeqKey(tenantId));
        MemoryEntry entry = new MemoryEntry(
                entryId(tenantId, sessionId, sequence),
                memoryLayer,
                memoryType,
                content,
                metadata == null ? Map.of() : Map.copyOf(metadata),
                traceId,
                Instant.now(),
                sequence,
                storageSource(memoryLayer, true)
        );
        String encoded = MemoryStorageCodec.encode(entry);
        String encodedVector = MemoryVectorSupport.serialize(MemoryVectorSupport.embed(content, vectorDim));
        redisTemplate.opsForZSet().add(redisTenantTimelineKey(tenantId), encoded, sequence);
        redisTemplate.opsForZSet().add(redisSessionTimelineKey(tenantId, sessionId), encoded, sequence);
        redisTemplate.opsForValue().set(redisVectorKey(entry.entryId()), encodedVector);
    }

    private static List<MemoryEntry> queryEntriesRedis(StringRedisTemplate redisTemplate, MemoryTimelineRequest request) {
        return queryEntriesRedis(redisTemplate, request, Math.max((request.limit() > 0 ? request.limit() : DEFAULT_LIMIT) * REDIS_FETCH_MULTIPLIER, 100));
    }

    private static List<MemoryEntry> queryEntriesRedis(StringRedisTemplate redisTemplate,
                                                       MemoryTimelineRequest request,
                                                       int fetchSize) {
        int limit = request.limit() > 0 ? request.limit() : DEFAULT_LIMIT;
        String key = isBlank(request.sessionId())
                ? redisTenantTimelineKey(request.tenantId())
                : redisSessionTimelineKey(request.tenantId(), request.sessionId());
        var encodedEntries = redisTemplate.opsForZSet().reverseRange(key, 0, fetchSize - 1);
        if (encodedEntries == null || encodedEntries.isEmpty()) {
            return List.of();
        }

        return encodedEntries.stream()
                .map(MemoryStorageCodec::decode)
                .filter(Objects::nonNull)
                .filter(entry -> request.memoryLayer() == null || request.memoryLayer() == entry.memoryLayer())
                .filter(entry -> isBlank(request.memoryType()) || request.memoryType().equals(entry.memoryType()))
                .filter(entry -> isBlank(request.keyword()) || entry.content().contains(request.keyword()))
                .filter(entry -> matchesMetadata(entry, request.metadataFilters()))
                .limit(limit)
                .toList();
    }

    private static String key(String tenantId, String sessionId) {
        return tenantId + "::" + sessionId;
    }

    private static boolean useRedis(String memoryMode, StringRedisTemplate redisTemplate) {
        return "redis".equalsIgnoreCase(memoryMode) && redisTemplate != null;
    }

    private static String redisSeqKey(String tenantId) {
        return "dish:memory:" + tenantId + ":seq";
    }

    private static String redisTenantTimelineKey(String tenantId) {
        return "dish:memory:" + tenantId + ":timeline";
    }

    private static String redisSessionTimelineKey(String tenantId, String sessionId) {
        return "dish:memory:" + tenantId + ":session:" + sessionId + ":timeline";
    }

    private static String redisApprovalKey(String tenantId, String sessionId, String approvalId) {
        return "dish:memory:" + tenantId + ":session:" + sessionId + ":approval:" + approvalId;
    }

    private static String redisVectorKey(String entryId) {
        return "dish:memory:vector:" + entryId;
    }

    private static String entryId(String tenantId, String sessionId, long sequence) {
        return tenantId + "-" + sessionId + "-" + sequence;
    }

    private static boolean matchesMetadata(MemoryEntry entry, Map<String, Object> filters) {
        if (filters == null || filters.isEmpty()) {
            return true;
        }
        for (Map.Entry<String, Object> filter : filters.entrySet()) {
            if (!Objects.equals(entry.metadata().get(filter.getKey()), filter.getValue())) {
                return false;
            }
        }
        return true;
    }

    private double semanticScore(MemoryEntry entry, double[] queryVector) {
        if (queryVector == null || queryVector.length == 0) {
            return 0;
        }
        if (useRedis(memoryMode, redisTemplate) && entry.entryId() != null) {
            String payload = redisTemplate.opsForValue().get(redisVectorKey(entry.entryId()));
            if (payload != null) {
                return MemoryVectorSupport.cosine(queryVector, MemoryVectorSupport.deserialize(payload));
            }
        }
        return MemoryVectorSupport.cosine(queryVector, MemoryVectorSupport.embed(entry.content(), vectorDim));
    }

    private double scoreKeyword(String query, MemoryEntry entry) {
        return entry.content().contains(query) ? 1.0 : MemoryVectorSupport.keywordOverlap(query, entry.content());
    }

    private double recencyScore(MemoryEntry entry) {
        return Math.min(entry.sequence() / 10_000_000_000.0, 0.15);
    }

    private MemoryRetrievalHit toHit(RetrievalCandidate candidate) {
        return new MemoryRetrievalHit(
                candidate.entry().memoryType(),
                candidate.entry().memoryLayer(),
                candidate.entry().content(),
                candidate.entry().metadata(),
                candidate.entry().traceId(),
                candidate.entry().createdAt(),
                candidate.entry().sequence(),
                candidate.entry().storageSource(),
                candidate.totalScore(),
                candidate.keywordScore(),
                candidate.vectorScore(),
                candidate.recencyScore(),
                candidate.explanation()
        );
    }

    private String explain(String source, double keywordScore, double vectorScore, double recencyScore, MemoryLayer layer) {
        return "layer=" + layer.name()
                + ", source=" + source
                + ", keyword=" + String.format("%.3f", keywordScore)
                + ", vector=" + String.format("%.3f", vectorScore)
                + ", recency=" + String.format("%.3f", recencyScore);
    }

    private List<MemoryLayer> normalizedLayers(List<MemoryLayer> layers) {
        if (layers == null || layers.isEmpty()) {
            return List.of(MemoryLayer.SHORT_TERM_SESSION, MemoryLayer.APPROVAL, MemoryLayer.LONG_TERM_KNOWLEDGE);
        }
        Set<MemoryLayer> dedup = new LinkedHashSet<>(layers);
        return List.copyOf(dedup);
    }

    private static boolean isBlank(String value) {
        return value == null || value.isBlank();
    }

    private static String storageSource(MemoryLayer layer, boolean redis) {
        String prefix = redis ? "redis" : "in-memory";
        return switch (layer) {
            case SHORT_TERM_SESSION -> prefix + "-short-term";
            case APPROVAL -> prefix + "-approval";
            case LONG_TERM_KNOWLEDGE -> prefix + "-long-term";
        };
    }

    private record RetrievalCandidate(MemoryEntry entry,
                                      double totalScore,
                                      double keywordScore,
                                      double vectorScore,
                                      double recencyScore,
                                      String explanation) {
    }

    static record MemoryEntry(String entryId,
                              MemoryLayer memoryLayer,
                              String memoryType,
                              String content,
                              Map<String, Object> metadata,
                              String traceId,
                              Instant createdAt,
                              long sequence,
                              String storageSource) {
    }
}
