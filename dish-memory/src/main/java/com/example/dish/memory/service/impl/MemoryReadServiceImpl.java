package com.example.dish.memory.service.impl;

import com.example.dish.control.memory.model.MemoryReadRequest;
import com.example.dish.control.memory.model.MemoryReadResult;
import com.example.dish.control.memory.service.MemoryReadService;
import com.example.dish.memory.retrieval.MemoryRetrievalEngine;
import com.example.dish.memory.storage.MemoryEntryStorage;
import com.example.dish.common.telemetry.DubboProviderSpan;
import org.apache.commons.lang3.StringUtils;
import org.apache.dubbo.config.annotation.DubboService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * 记忆读服务门面。
 *
 * <p>该类只保留 Dubbo 入口职责：校验请求并委托 retrieval 层完成召回。</p>
 */
@Service
@DubboService(interfaceClass = MemoryReadService.class, timeout = 5000, retries = 0)
public class MemoryReadServiceImpl implements MemoryReadService {

    @Autowired
    private MemoryRetrievalEngine memoryRetrievalEngine;

    @Override
    @DubboProviderSpan("memory.read")
    public MemoryReadResult read(MemoryReadRequest request) {
        if (request == null || StringUtils.isBlank(request.tenantId()) || StringUtils.isBlank(request.sessionId())) {
            return new MemoryReadResult(List.of(), "none", false, List.of());
        }

        // 1. 委托召回组件完成候选收集、打分和结果解释。
        return memoryRetrievalEngine.retrieve(request);
    }

    static void clearForTest() {
        MemoryEntryStorage.clearForTest();
    }
}
