package com.example.dish.memory.retrieval;

import com.example.dish.control.memory.model.MemoryLayer;
import com.example.dish.control.memory.model.MemoryReadRequest;
import com.example.dish.control.memory.model.MemoryReadResult;
import com.example.dish.control.memory.model.MemoryTimelineRequest;
import com.example.dish.memory.model.MemoryEntry;
import com.example.dish.memory.storage.LongTermMemoryVectorStore;
import com.example.dish.memory.storage.MemoryTimelineQueryStorage;
import com.example.dish.memory.storage.MemoryVectorIndexStorage;
import com.example.dish.memory.support.MemoryVectorSupport;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 记忆召回编排器。
 *
 * <p>该类负责把一次 read 请求拆成“候选收集 -> 混合打分 -> 结果组装”三段，
 * 自己不直接对外暴露 Dubbo 接口，也不直接承担结果 DTO 的最终拼装。</p>
 */
@Component
public class MemoryRetrievalEngine {

    private static final int DEFAULT_LIMIT = 5;
    private static final int REDIS_FETCH_MULTIPLIER = 20;

    @Value("${memory.retrieval.vector-dim:128}")
    private int vectorDim = 128;

    @Value("${memory.retrieval.candidate-fetch-size:200}")
    private int candidateFetchSize = 200;

    @Value("${memory.retrieval.keyword-weight:0.45}")
    private double keywordWeight = 0.45;

    @Value("${memory.retrieval.vector-weight:0.55}")
    private double vectorWeight = 0.55;

    @Autowired(required = false)
    private LongTermMemoryVectorStore longTermMemoryVectorStore;

    @Autowired
    private MemoryTimelineQueryStorage memoryTimelineQueryStorage;

    @Autowired
    private MemoryVectorIndexStorage memoryVectorIndexStorage;

    @Autowired
    private MemoryRetrievalResultAssembler memoryRetrievalResultAssembler;

    public MemoryReadResult retrieve(MemoryReadRequest request) {
        // 1. 归一化查询条件和记忆层范围。
        int limit = request.limit() > 0 ? request.limit() : DEFAULT_LIMIT;
        List<MemoryLayer> layers = normalizedLayers(request.memoryLayers());
        String query = request.query();
        double[] queryVector = StringUtils.isBlank(query) ? new double[0] : MemoryVectorSupport.embed(query, vectorDim);

        // 2. 从短期/审批存储和长期知识库收集候选记忆。
        List<MemoryRetrievalCandidate> candidates = new ArrayList<>();
        candidates.addAll(retrieveOperationalMemory(request, layers, limit, query, queryVector));
        candidates.addAll(retrieveLongTermMemory(request, layers, limit, query));

        // 3. 委托结果组装器完成排序、命中解释和 DTO 转换。
        return memoryRetrievalResultAssembler.assemble(candidates, limit);
    }

    private List<MemoryRetrievalCandidate> retrieveOperationalMemory(MemoryReadRequest request,
                                                                     List<MemoryLayer> layers,
                                                                     int limit,
                                                                     String query,
                                                                     double[] queryVector) {
        // 1. 先从请求层范围里排除长期知识，只保留短期会话和审批类候选。
        List<MemoryLayer> operationalLayers = layers.stream()
                .filter(layer -> layer != MemoryLayer.LONG_TERM_KNOWLEDGE)
                .toList();
        if (operationalLayers.isEmpty()) {
            return List.of();
        }

        // 2. 组装一次统一的时间线候选查询，交给查询存储层按当前存储模式取数。
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
        List<MemoryEntry> candidates = memoryTimelineQueryStorage.collectRetrievalCandidates(timelineRequest, candidateFetchSize, operationalLayers);
        if (candidates.isEmpty()) {
            return List.of();
        }

        // 3. 对每条候选记录计算 keyword / vector / recency 三段分数，并保留解释文本。
        return candidates.stream()
                .map(entry -> {
                    double keywordScore = StringUtils.isBlank(query) ? 0.0 : scoreKeyword(query, entry);
                    double vectorScore = StringUtils.isBlank(query) ? 0.0 : semanticScore(entry, queryVector);
                    double recencyScore = recencyScore(entry);
                    double totalScore = StringUtils.isBlank(query)
                            ? recencyScore + 1.0
                            : keywordWeight * keywordScore + vectorWeight * vectorScore + recencyScore;
                    return new MemoryRetrievalCandidate(
                            entry,
                            totalScore,
                            keywordScore,
                            vectorScore,
                            recencyScore,
                            memoryRetrievalResultAssembler.explain(entry.storageSource(), keywordScore, vectorScore, recencyScore, entry.memoryLayer())
                    );
                })
                .toList();
    }

    private List<MemoryRetrievalCandidate> retrieveLongTermMemory(MemoryReadRequest request,
                                                                  List<MemoryLayer> layers,
                                                                  int limit,
                                                                  String query) {
        // 1. 只有显式包含长期知识层、查询词非空且向量库可用时，才走长期知识召回。
        if (!layers.contains(MemoryLayer.LONG_TERM_KNOWLEDGE) || StringUtils.isBlank(query) || longTermMemoryVectorStore == null) {
            return List.of();
        }

        // 2. 先从长期向量库拿命中结果，再转换成统一的内部候选模型。
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
                    // 3. 长期知识沿用统一的混合分数模型，保证和短期候选可直接一起排序。
                    return new MemoryRetrievalCandidate(
                            entry,
                            keywordWeight * keywordScore + vectorWeight * vectorScore + recencyScore,
                            keywordScore,
                            vectorScore,
                            recencyScore,
                            memoryRetrievalResultAssembler.explain(hit.retrievalSource(), keywordScore, vectorScore, recencyScore, MemoryLayer.LONG_TERM_KNOWLEDGE)
                    );
                })
                .toList();
    }

    private double semanticScore(MemoryEntry entry, double[] queryVector) {
        // 向量分数统一委托向量索引层，避免 retrieval 自己关心 Redis 向量缓存细节。
        return memoryVectorIndexStorage.vectorScore(entry, queryVector);
    }

    private double scoreKeyword(String query, MemoryEntry entry) {
        // 完全包含命中直接给满分，否则退回 token overlap 做近似关键词命中。
        return entry.content().contains(query) ? 1.0 : MemoryVectorSupport.keywordOverlap(query, entry.content());
    }

    private double recencyScore(MemoryEntry entry) {
        // 当前用 sequence 近似新近度，分值上限控制在 0.15，避免新近度压过语义相关性。
        return Math.min(entry.sequence() / 10_000_000_000.0, 0.15);
    }

    private List<MemoryLayer> normalizedLayers(List<MemoryLayer> layers) {
        if (layers == null || layers.isEmpty()) {
            return List.of(MemoryLayer.SHORT_TERM_SESSION, MemoryLayer.APPROVAL, MemoryLayer.LONG_TERM_KNOWLEDGE);
        }
        Set<MemoryLayer> dedup = new LinkedHashSet<>(layers);
        return List.copyOf(dedup);
    }
}
