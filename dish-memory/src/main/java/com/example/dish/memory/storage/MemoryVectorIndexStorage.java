package com.example.dish.memory.storage;

import com.example.dish.memory.model.MemoryEntry;
import com.example.dish.memory.support.MemoryKeySupport;
import com.example.dish.memory.support.MemoryVectorSupport;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

/**
 * 记忆向量索引存储。
 *
 * <p>该类负责把 memory entry 的向量缓存到 Redis，
 * 并在召回阶段提供统一的向量分数计算入口。</p>
 */
@Component
public class MemoryVectorIndexStorage {

    @Value("${memory.mode:bootstrap}")
    private String memoryMode = "bootstrap";

    @Value("${memory.retrieval.vector-dim:128}")
    private int vectorDim = 128;

    @Autowired(required = false)
    private StringRedisTemplate redisTemplate;

    public void saveVector(MemoryEntry entry) {
        // 只有 Redis 模式才真正维护独立向量索引；本地模式直接按内容现算。
        if (!usesRedis() || entry == null || entry.entryId() == null) {
            return;
        }
        String encodedVector = MemoryVectorSupport.serialize(MemoryVectorSupport.embed(entry.content(), vectorDim));
        redisTemplate.opsForValue().set(MemoryKeySupport.vectorKey(entry.entryId()), encodedVector);
    }

    public double vectorScore(MemoryEntry entry, double[] queryVector) {
        // 1. 查询向量为空或 entry 为空时没有计算价值，直接返回 0。
        if (queryVector == null || queryVector.length == 0 || entry == null) {
            return 0.0;
        }
        // 2. Redis 模式优先读取已缓存向量，避免每次召回都重复 embed。
        if (usesRedis() && entry.entryId() != null) {
            String payload = redisTemplate.opsForValue().get(MemoryKeySupport.vectorKey(entry.entryId()));
            if (payload != null) {
                return MemoryVectorSupport.cosine(queryVector, MemoryVectorSupport.deserialize(payload));
            }
        }
        // 3. 本地模式或缓存缺失时退回即时向量化，保证召回逻辑不因缓存缺失而中断。
        return MemoryVectorSupport.cosine(queryVector, MemoryVectorSupport.embed(entry.content(), vectorDim));
    }

    private boolean usesRedis() {
        return "redis".equalsIgnoreCase(memoryMode) && redisTemplate != null;
    }
}
