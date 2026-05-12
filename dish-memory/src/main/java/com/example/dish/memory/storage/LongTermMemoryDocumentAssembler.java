package com.example.dish.memory.storage;

import com.example.dish.control.memory.model.MemoryLayer;
import com.example.dish.control.memory.model.MemoryWriteRequest;
import com.example.dish.memory.model.LongTermMemoryDocument;
import com.example.dish.memory.model.LongTermMemoryHit;
import dev.langchain4j.data.document.Metadata;
import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.store.embedding.EmbeddingMatch;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

/**
 * 长期记忆文档装配器。
 * 负责把写入请求转换为稳定的长期知识文档，并把向量库命中还原为召回结果；
 * 不参与向量库初始化和检索编排。
 */
@Component
public class LongTermMemoryDocumentAssembler {

    private static final String GLOBAL_TENANT = "GLOBAL";

    public LongTermMemoryDocument toDocument(MemoryWriteRequest request) {
        // 1. 为长期知识补齐 tenant、session、trace、sequence 等稳定元数据。
        Instant now = Instant.now();
        long sequence = now.toEpochMilli();
        Map<String, Object> metadata = new LinkedHashMap<>();
        if (request.metadata() != null) {
            metadata.putAll(request.metadata());
        }
        metadata.put("tenantId", StringUtils.isBlank(request.tenantId()) ? GLOBAL_TENANT : request.tenantId());
        metadata.put("sessionId", request.sessionId());
        metadata.put("memoryType", request.memoryType());
        metadata.put("memoryLayer", request.memoryLayer().name());
        metadata.put("traceId", request.traceId());
        metadata.put("createdAt", now.toString());
        metadata.put("sequence", sequence);

        // 2. 用租户、会话、类型和内容生成稳定文档 ID，避免重复 bootstrap 时出现雪崩式重复写入。
        String id = UUID.nameUUIDFromBytes((
                metadata.get("tenantId") + "::" +
                        request.sessionId() + "::" +
                        request.memoryType() + "::" +
                        request.content()
        ).getBytes(StandardCharsets.UTF_8)).toString();

        return new LongTermMemoryDocument(
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

    public LongTermMemoryHit toHit(
            EmbeddingMatch<TextSegment> match,
            Map<String, LongTermMemoryDocument> catalog,
            String retrievalSource
    ) {
        if (match == null || match.embedded() == null) {
            return null;
        }

        TextSegment segment = match.embedded();
        Metadata metadata = segment.metadata();
        String documentId = match.embeddingId();
        LongTermMemoryDocument document = documentId != null ? catalog.get(documentId) : null;
        Map<String, Object> documentMetadata = document != null ? document.metadata() : metadata.toMap();

        return new LongTermMemoryHit(
                documentId,
                metadata.getString("memoryType"),
                MemoryLayer.LONG_TERM_KNOWLEDGE,
                segment.text(),
                documentMetadata,
                metadata.getString("traceId"),
                parseInstant(metadata.getString("createdAt")),
                metadata.getLong("sequence") != null ? metadata.getLong("sequence") : 0L,
                retrievalSource,
                match.score() != null ? match.score() : 0.0
        );
    }

    private Instant parseInstant(String value) {
        if (StringUtils.isBlank(value)) {
            return Instant.now();
        }
        return Instant.parse(value);
    }
}
