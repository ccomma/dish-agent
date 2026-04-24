package com.example.dish.memory.service.impl;

import com.example.dish.control.memory.model.MemoryWriteRequest;
import com.example.dish.control.memory.service.MemoryWriteService;
import com.example.dish.memory.storage.MemoryEntryStorage;
import com.example.dish.memory.storage.LongTermMemoryVectorStore;
import com.example.dish.common.telemetry.DubboProviderSpan;
import org.apache.commons.lang3.StringUtils;
import org.apache.dubbo.config.annotation.DubboService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

/**
 * 记忆写服务门面。
 *
 * <p>该类负责接收统一的 memory write 请求，并根据记忆层类型把数据写入长期知识层或时间线层。</p>
 */
@Service
@DubboService(interfaceClass = MemoryWriteService.class, timeout = 5000, retries = 0)
public class MemoryWriteServiceImpl implements MemoryWriteService {

    @Autowired(required = false)
    private LongTermMemoryVectorStore longTermMemoryVectorStore;

    @Autowired
    private MemoryEntryStorage memoryEntryStorage;

    @Override
    @DubboProviderSpan("memory.write")
    public boolean write(MemoryWriteRequest request) {
        // 1. 校验写入请求，避免空租户、空会话或空内容进入记忆层。
        if (request == null
                || StringUtils.isBlank(request.content())
                || request.memoryLayer() == null
                || StringUtils.isBlank(request.tenantId())
                || StringUtils.isBlank(request.sessionId())) {
            return false;
        }

        // 2. 长期知识先写入向量库，保证后续语义召回可用。
        if (request.memoryLayer() == com.example.dish.control.memory.model.MemoryLayer.LONG_TERM_KNOWLEDGE) {
            if (longTermMemoryVectorStore == null) {
                return false;
            }
            longTermMemoryVectorStore.index(request);
        }

        // 3. 所有记忆统一进入时间线存储，供控制台和短期召回查询。
        memoryEntryStorage.append(
                request.tenantId(),
                request.sessionId(),
                request.memoryLayer(),
                request.memoryType(),
                request.content(),
                request.metadata(),
                request.traceId()
        );
        return true;
    }
}
