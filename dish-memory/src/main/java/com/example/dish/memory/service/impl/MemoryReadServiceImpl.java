package com.example.dish.memory.service.impl;

import com.example.dish.control.memory.model.MemoryReadRequest;
import com.example.dish.control.memory.model.MemoryReadResult;
import com.example.dish.control.memory.model.MemoryTimelineRequest;
import com.example.dish.control.memory.service.MemoryReadService;
import org.apache.dubbo.config.annotation.DubboService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicLong;

@Service
@DubboService(interfaceClass = MemoryReadService.class, timeout = 5000, retries = 0)
public class MemoryReadServiceImpl implements MemoryReadService {

    private static final int MAX_RETURN_SNIPPETS = 5;
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

    @Override
    public MemoryReadResult read(MemoryReadRequest request) {
        if (request == null || isBlank(request.tenantId()) || isBlank(request.sessionId())) {
            return new MemoryReadResult(List.of(), "in-memory", false);
        }

        List<String> snippets = retrieveSnippets(request);

        return new MemoryReadResult(snippets, source(memoryMode, redisTemplate), !snippets.isEmpty());
    }

    private List<String> retrieveSnippets(MemoryReadRequest request) {
        MemoryTimelineRequest timelineRequest = new MemoryTimelineRequest(
                request.tenantId(),
                request.sessionId(),
                null,
                null,
                Map.of(),
                MAX_RETURN_SNIPPETS,
                request.traceId()
        );
        List<MemoryEntry> candidates = collectRetrievalCandidates(memoryMode, redisTemplate, timelineRequest, candidateFetchSize);
        if (candidates.isEmpty()) {
            return List.of();
        }

        String query = request.query();
        if (isBlank(query)) {
            return candidates.stream()
                    .sorted(Comparator.comparingLong(MemoryEntry::sequence).reversed())
                    .limit(MAX_RETURN_SNIPPETS)
                    .map(MemoryEntry::content)
                    .toList();
        }

        double[] queryVector = MemoryVectorSupport.embed(query, vectorDim);
        return candidates.stream()
                .map(entry -> new RetrievalCandidate(
                        entry,
                        scoreEntry(query, queryVector, entry)
                ))
                .sorted(Comparator.comparingDouble(RetrievalCandidate::score).reversed()
                        .thenComparing(candidate -> candidate.entry().sequence(), Comparator.reverseOrder()))
                .limit(MAX_RETURN_SNIPPETS)
                .map(candidate -> candidate.entry().content())
                .toList();
    }

    static void append(String memoryMode,
                       StringRedisTemplate redisTemplate,
                       int vectorDim,
                       String tenantId,
                       String sessionId,
                       String memoryType,
                       String content,
                       Map<String, Object> metadata,
                       String traceId) {
        if (useRedis(memoryMode, redisTemplate)) {
            appendRedis(redisTemplate, vectorDim, tenantId, sessionId, memoryType, content, metadata, traceId);
            return;
        }

        long sequence = SEQ.incrementAndGet();
        String key = key(tenantId, sessionId);
        STORE.computeIfAbsent(key, ignored -> new CopyOnWriteArrayList<>())
                .add(new MemoryEntry(
                        entryId(tenantId, sessionId, sequence),
                        memoryType,
                        content,
                        metadata == null ? Map.of() : Map.copyOf(metadata),
                        traceId,
                        Instant.now(),
                        sequence
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
        int limit = request.limit() > 0 ? request.limit() : MAX_RETURN_SNIPPETS;

        return entries.stream()
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
                                                        int fetchSize) {
        if (request == null || isBlank(request.tenantId())) {
            return List.of();
        }
        if (useRedis(memoryMode, redisTemplate)) {
            return queryEntriesRedis(redisTemplate, request, fetchSize);
        }
        List<MemoryEntry> entries = collectEntries(request.tenantId(), request.sessionId());
        return entries.stream()
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
        return useRedis(memoryMode, redisTemplate) ? "redis+vector" : "in-memory+vector";
    }

    private static void appendRedis(StringRedisTemplate redisTemplate,
                                    int vectorDim,
                                    String tenantId,
                                    String sessionId,
                                    String memoryType,
                                    String content,
                                    Map<String, Object> metadata,
                                    String traceId) {
        long sequence = redisTemplate.opsForValue().increment(redisSeqKey(tenantId));
        MemoryEntry entry = new MemoryEntry(
                entryId(tenantId, sessionId, sequence),
                memoryType,
                content,
                metadata == null ? Map.of() : Map.copyOf(metadata),
                traceId,
                Instant.now(),
                sequence
        );
        String encoded = MemoryStorageCodec.encode(entry);
        String encodedVector = MemoryVectorSupport.serialize(MemoryVectorSupport.embed(content, vectorDim));
        redisTemplate.opsForZSet().add(redisTenantTimelineKey(tenantId), encoded, sequence);
        redisTemplate.opsForZSet().add(redisSessionTimelineKey(tenantId, sessionId), encoded, sequence);
        redisTemplate.opsForValue().set(redisVectorKey(entry.entryId()), encodedVector);
    }

    private static List<MemoryEntry> queryEntriesRedis(StringRedisTemplate redisTemplate, MemoryTimelineRequest request) {
        return queryEntriesRedis(redisTemplate, request, Math.max((request.limit() > 0 ? request.limit() : MAX_RETURN_SNIPPETS) * REDIS_FETCH_MULTIPLIER, 100));
    }

    private static List<MemoryEntry> queryEntriesRedis(StringRedisTemplate redisTemplate,
                                                       MemoryTimelineRequest request,
                                                       int fetchSize) {
        int limit = request.limit() > 0 ? request.limit() : MAX_RETURN_SNIPPETS;
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

    private static boolean isBlank(String value) {
        return value == null || value.isBlank();
    }

    private double scoreEntry(String query, double[] queryVector, MemoryEntry entry) {
        double keywordScore = entry.content().contains(query) ? 1.0 : MemoryVectorSupport.keywordOverlap(query, entry.content());
        double vectorScore = semanticScore(entry, queryVector);
        double recencyBonus = Math.min(entry.sequence() / 10_000.0, 0.15);
        return keywordWeight * keywordScore + vectorWeight * vectorScore + recencyBonus;
    }

    private double semanticScore(MemoryEntry entry, double[] queryVector) {
        if (useRedis(memoryMode, redisTemplate) && entry.entryId() != null) {
            String payload = redisTemplate.opsForValue().get(redisVectorKey(entry.entryId()));
            if (payload != null) {
                return MemoryVectorSupport.cosine(queryVector, MemoryVectorSupport.deserialize(payload));
            }
        }
        return MemoryVectorSupport.cosine(queryVector, MemoryVectorSupport.embed(entry.content(), vectorDim));
    }

    private record RetrievalCandidate(MemoryEntry entry, double score) {
    }

    static record MemoryEntry(String entryId,
                              String memoryType,
                              String content,
                              Map<String, Object> metadata,
                              String traceId,
                              Instant createdAt,
                              long sequence) {
    }
}
