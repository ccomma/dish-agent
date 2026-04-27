package com.example.dish.gateway.service.impl;

import com.example.dish.common.contract.RoutingDecision;
import com.example.dish.control.memory.model.MemoryLayer;
import com.example.dish.control.memory.model.MemoryWriteRequest;
import com.example.dish.control.memory.service.MemoryWriteService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * execution summary 写入器。
 * 负责把 gateway 执行结果写入短期会话记忆，并在成功时沉淀长期运营知识。
 */
public class ExecutionSummaryWriter {

    private static final Logger log = LoggerFactory.getLogger(ExecutionSummaryWriter.class);

    public void write(MemoryWriteService memoryWriteService,
                      RoutingDecision routing,
                      int executedStepCount,
                      boolean success,
                      String traceId) {
        if (memoryWriteService == null) {
            return;
        }

        String sessionId = routing != null && routing.context() != null ? routing.context().getSessionId() : null;
        String storeId = routing != null && routing.context() != null ? routing.context().getStoreId() : null;

        // 1. 先写短期 execution summary，供会话时间线和 dashboard 查询。
        boolean writeOk = memoryWriteService.write(summaryRequest(storeId, sessionId, routing, executedStepCount, success, traceId));
        if (!writeOk) {
            log.warn("memory write skipped or failed: sessionId={}, traceId={}", sessionId, traceId);
        }

        // 2. 成功执行过实际步骤时，再把可复用经验提升到长期知识层。
        if (success && executedStepCount > 0) {
            memoryWriteService.write(knowledgeRequest(storeId, sessionId, routing, executedStepCount, traceId));
        }
    }

    private MemoryWriteRequest summaryRequest(String storeId,
                                              String sessionId,
                                              RoutingDecision routing,
                                              int executedStepCount,
                                              boolean success,
                                              String traceId) {
        String summary = "planId=" + (routing != null ? routing.planId() : null)
                + ", intent=" + (routing != null && routing.intent() != null ? routing.intent().name() : null)
                + ", executionMode=" + (routing != null ? routing.executionMode() : null)
                + ", executedSteps=" + executedStepCount
                + ", success=" + success;

        return new MemoryWriteRequest(
                storeId,
                sessionId,
                MemoryLayer.SHORT_TERM_SESSION,
                "execution_summary",
                summary,
                metadataMap(
                        "traceId", traceId,
                        "planId", routing != null ? routing.planId() : null,
                        "executionMode", routing != null ? routing.executionMode() : null,
                        "success", success,
                        "executedStepCount", executedStepCount,
                        "sessionId", sessionId,
                        "storeId", storeId
                ),
                traceId
        );
    }

    private MemoryWriteRequest knowledgeRequest(String storeId,
                                                String sessionId,
                                                RoutingDecision routing,
                                                int executedStepCount,
                                                String traceId) {
        return new MemoryWriteRequest(
                storeId,
                sessionId,
                MemoryLayer.LONG_TERM_KNOWLEDGE,
                "operational_knowledge",
                "intent=" + (routing != null && routing.intent() != null ? routing.intent().name() : null)
                        + ", executionMode=" + (routing != null ? routing.executionMode() : null)
                        + ", executedSteps=" + executedStepCount
                        + ", outcome=success",
                metadataMap(
                        "traceId", traceId,
                        "planId", routing != null ? routing.planId() : null,
                        "sessionId", sessionId,
                        "storeId", storeId,
                        "promotedFrom", "execution_summary"
                ),
                traceId
        );
    }

    private Map<String, Object> metadataMap(Object... keyValues) {
        Map<String, Object> metadata = new LinkedHashMap<>();
        for (int i = 0; i < keyValues.length - 1; i += 2) {
            Object key = keyValues[i];
            Object value = keyValues[i + 1];
            if (key instanceof String text && value != null) {
                metadata.put(text, value);
            }
        }
        return metadata;
    }
}
