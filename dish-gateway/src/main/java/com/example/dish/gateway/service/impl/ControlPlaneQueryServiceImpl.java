package com.example.dish.gateway.service.impl;

import com.example.dish.common.runtime.ApprovalTicket;
import com.example.dish.common.runtime.ExecutionNodeStatus;
import com.example.dish.control.approval.model.ApprovalDecisionAction;
import com.example.dish.control.approval.model.ApprovalTicketDecisionRequest;
import com.example.dish.control.approval.model.ApprovalTicketQueryRequest;
import com.example.dish.control.approval.model.ApprovalTicketQueryResult;
import com.example.dish.control.approval.service.ApprovalTicketService;
import com.example.dish.control.execution.model.ExecutionGraphQueryRequest;
import com.example.dish.control.execution.model.ExecutionGraphViewResult;
import com.example.dish.control.execution.model.ExecutionLatestQueryRequest;
import com.example.dish.control.execution.model.ExecutionReplayQueryRequest;
import com.example.dish.control.execution.model.ExecutionReplayResult;
import com.example.dish.control.execution.service.ExecutionRuntimeReadService;
import com.example.dish.control.memory.model.MemoryLayer;
import com.example.dish.control.memory.model.MemoryReadRequest;
import com.example.dish.control.memory.model.MemoryReadResult;
import com.example.dish.control.memory.model.MemoryTimelineEntry;
import com.example.dish.control.memory.model.MemoryTimelineRequest;
import com.example.dish.control.memory.model.MemoryTimelineResult;
import com.example.dish.control.memory.service.MemoryReadService;
import com.example.dish.control.memory.service.MemoryTimelineService;
import com.example.dish.control.support.ApprovalTicketCodec;
import com.example.dish.gateway.dto.control.ApprovalDecisionResponse;
import com.example.dish.gateway.dto.control.ApprovalTicketViewResponse;
import com.example.dish.gateway.dto.control.ControlDashboardOverviewResponse;
import com.example.dish.gateway.dto.control.DashboardApprovalItemResponse;
import com.example.dish.gateway.dto.control.DashboardSessionItemResponse;
import com.example.dish.gateway.dto.control.SessionMemoryEntryResponse;
import com.example.dish.gateway.dto.control.SessionMemoryRetrievalHitResponse;
import com.example.dish.gateway.dto.control.SessionMemoryRetrievalResponse;
import com.example.dish.gateway.dto.control.SessionMemoryTimelineResponse;
import com.example.dish.gateway.observability.ExecutionMetricsService;
import com.example.dish.gateway.service.ControlPlaneQueryService;
import com.example.dish.gateway.service.ExecutionResumeService;
import org.apache.dubbo.config.annotation.DubboReference;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import javax.annotation.Resource;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Service
public class ControlPlaneQueryServiceImpl implements ControlPlaneQueryService {

    private static final Logger log = LoggerFactory.getLogger(ControlPlaneQueryServiceImpl.class);

    @DubboReference(timeout = 5000, retries = 0, check = false)
    private MemoryReadService memoryReadService;

    @DubboReference(timeout = 5000, retries = 0, check = false)
    private MemoryTimelineService memoryTimelineService;

    @DubboReference(timeout = 5000, retries = 0, check = false)
    private ApprovalTicketService approvalTicketService;

    @DubboReference(timeout = 5000, retries = 0, check = false)
    private ExecutionRuntimeReadService executionRuntimeReadService;

    @Resource
    private ExecutionResumeService executionResumeService;
    @Resource
    private ExecutionMetricsService executionMetricsService;

    @Override
    public SessionMemoryTimelineResponse getSessionTimeline(String storeId,
                                                            String sessionId,
                                                            String memoryType,
                                                            String keyword,
                                                            int limit,
                                                            String traceId) {
        MemoryTimelineResult result = memoryTimelineService.timeline(new MemoryTimelineRequest(
                storeId,
                sessionId,
                memoryType,
                null,
                keyword,
                Map.of(),
                limit > 0 ? limit : 20,
                traceId
        ));

        List<SessionMemoryEntryResponse> entries = result.entries().stream()
                .map(entry -> new SessionMemoryEntryResponse(
                        entry.memoryType(),
                        entry.memoryLayer() != null ? entry.memoryLayer().name() : null,
                        entry.content(),
                        entry.metadata(),
                        entry.traceId(),
                        entry.createdAt(),
                        entry.sequence(),
                        entry.storageSource()
                ))
                .toList();

        return new SessionMemoryTimelineResponse(sessionId, storeId, result.source(), result.total(), entries);
    }

    @Override
    public SessionMemoryRetrievalResponse retrieveSessionMemory(String storeId,
                                                               String sessionId,
                                                               String query,
                                                               String layers,
                                                               int limit,
                                                               String traceId) {
        MemoryReadResult result = memoryReadService.read(new MemoryReadRequest(
                storeId,
                sessionId,
                query,
                parseLayers(layers),
                limit > 0 ? limit : 5,
                traceId
        ));

        List<SessionMemoryRetrievalHitResponse> hits = result.hits().stream()
                .map(hit -> new SessionMemoryRetrievalHitResponse(
                        hit.memoryType(),
                        hit.memoryLayer() != null ? hit.memoryLayer().name() : null,
                        hit.content(),
                        hit.metadata(),
                        hit.traceId(),
                        hit.createdAt(),
                        hit.sequence(),
                        hit.retrievalSource(),
                        hit.totalScore(),
                        hit.keywordScore(),
                        hit.vectorScore(),
                        hit.recencyScore(),
                        hit.explanation()
                ))
                .toList();

        return new SessionMemoryRetrievalResponse(sessionId, storeId, result.source(), hits.size(), hits);
    }

    @Override
    public ExecutionGraphViewResult getLatestExecution(String storeId, String sessionId, String traceId) {
        return executionRuntimeReadService.latest(new ExecutionLatestQueryRequest(storeId, sessionId, traceId));
    }

    @Override
    public ExecutionGraphViewResult getExecutionGraph(String storeId, String executionId, String traceId) {
        return executionRuntimeReadService.graph(new ExecutionGraphQueryRequest(storeId, executionId, traceId));
    }

    @Override
    public ExecutionReplayResult getExecutionReplay(String storeId, String executionId, String traceId) {
        return executionRuntimeReadService.replay(new ExecutionReplayQueryRequest(storeId, executionId, traceId));
    }

    @Override
    public ApprovalTicketViewResponse getApprovalTicket(String storeId, String sessionId, String approvalId, String traceId) {
        ApprovalTicketQueryResult result = approvalTicketService.get(new ApprovalTicketQueryRequest(
                approvalId,
                storeId,
                sessionId,
                traceId
        ));
        if (result == null || !result.found() || result.ticket() == null) {
            return new ApprovalTicketViewResponse(
                    approvalId,
                    sessionId,
                    storeId,
                    traceId,
                    "NOT_FOUND",
                    null,
                    null,
                    null,
                    null,
                    false
            );
        }

        ApprovalTicket ticket = result.ticket();
        Object targetAgent = ticket.payload().get("targetAgent");
        Object ticketTraceId = ticket.metadata().get("traceId");
        return new ApprovalTicketViewResponse(
                ticket.ticketId(),
                sessionId,
                storeId,
                ticketTraceId instanceof String text ? text : traceId,
                ticket.status().name(),
                targetAgent instanceof String text ? text : null,
                ticket.requestedBy(),
                ticket.decisionReason(),
                ticket.createdAt(),
                true
        );
    }

    @Override
    public ApprovalDecisionResponse decideApprovalTicket(String storeId,
                                                         String sessionId,
                                                         String approvalId,
                                                         ApprovalDecisionAction action,
                                                         String decidedBy,
                                                         String decisionReason,
                                                         String traceId) {
        var result = approvalTicketService.decide(new ApprovalTicketDecisionRequest(
                approvalId,
                storeId,
                sessionId,
                traceId,
                action,
                decidedBy,
                decisionReason
        ));

        ApprovalTicket ticket = result != null ? result.ticket() : null;
        if (result != null && result.success() && ticket != null && ticket.executionId() != null) {
            try {
                if (action == ApprovalDecisionAction.APPROVE) {
                    executionResumeService.resumeApprovedExecution(storeId, sessionId, ticket.executionId(), traceId);
                } else {
                    executionResumeService.rejectExecution(
                            storeId,
                            sessionId,
                            ticket.executionId(),
                            traceId,
                            decisionReason != null ? decisionReason : "approval rejected"
                    );
                }
            } catch (Exception ex) {
                log.warn("approval follow-up failed: approvalId={}, executionId={}, action={}, message={}",
                        approvalId, ticket.executionId(), action, ex.getMessage(), ex);
            }
        }
        executionMetricsService.recordApprovalDecision(action, result != null && result.success());

        return new ApprovalDecisionResponse(
                result != null && result.success(),
                approvalId,
                ticket != null ? ticket.status().name() : "UNKNOWN",
                sessionId,
                storeId,
                traceId,
                ticket != null ? ticket.decidedBy() : decidedBy,
                ticket != null ? ticket.decisionReason() : decisionReason,
                ticket != null ? ticket.decidedAt() : null,
                result != null ? result.message() : "approval decision service returned empty result"
        );
    }

    @Override
    public ControlDashboardOverviewResponse getDashboardOverview(String storeId, int limit, String traceId) {
        MemoryTimelineResult result = memoryTimelineService.timeline(new MemoryTimelineRequest(
                storeId,
                null,
                null,
                null,
                null,
                Map.of(),
                limit > 0 ? Math.max(limit * 20, 50) : 50,
                traceId
        ));

        List<MemoryTimelineEntry> entries = result.entries();
        Map<String, Integer> memoryTypeBreakdown = new LinkedHashMap<>();
        Map<String, Integer> memoryLayerBreakdown = new LinkedHashMap<>();
        Map<String, DashboardSessionItemResponse> latestSessionMap = new LinkedHashMap<>();
        Map<String, DashboardApprovalItemResponse> latestApprovalMap = new LinkedHashMap<>();

        for (MemoryTimelineEntry entry : entries) {
            memoryTypeBreakdown.merge(entry.memoryType(), 1, Integer::sum);
            if (entry.memoryLayer() != null) {
                memoryLayerBreakdown.merge(entry.memoryLayer().name(), 1, Integer::sum);
            }
            String sessionId = asString(entry.metadata().get("sessionId"));
            if (sessionId != null) {
                latestSessionMap.merge(sessionId, toSessionItem(entry), this::pickLatest);
            }
            if ("approval_ticket".equals(entry.memoryType())) {
                DashboardApprovalItemResponse approvalItem = toApprovalItem(entry);
                if (approvalItem != null) {
                    latestApprovalMap.merge(approvalItem.approvalId(), approvalItem, this::pickLatestApproval);
                }
            }
        }

        int pending = 0;
        int approved = 0;
        int rejected = 0;
        for (DashboardApprovalItemResponse approval : latestApprovalMap.values()) {
            if ("PENDING".equals(approval.status())) {
                pending++;
            } else if ("APPROVED".equals(approval.status())) {
                approved++;
            } else if ("REJECTED".equals(approval.status())) {
                rejected++;
            }
        }

        List<DashboardApprovalItemResponse> recentApprovals = latestApprovalMap.values().stream()
                .sorted(Comparator.comparing(DashboardApprovalItemResponse::createdAt, Comparator.nullsLast(Comparator.reverseOrder())))
                .limit(limit > 0 ? limit : 10)
                .toList();

        List<DashboardSessionItemResponse> activeSessions = latestSessionMap.values().stream()
                .map(item -> enrichWithExecution(storeId, item, traceId))
                .sorted(Comparator.comparing(DashboardSessionItemResponse::lastUpdatedAt, Comparator.nullsLast(Comparator.reverseOrder())))
                .limit(limit > 0 ? limit : 10)
                .toList();

        int runningExecutionCount = (int) activeSessions.stream()
                .filter(item -> ExecutionNodeStatus.RUNNING.name().equals(item.latestExecutionStatus()))
                .count();
        int waitingApprovalExecutionCount = (int) activeSessions.stream()
                .filter(item -> ExecutionNodeStatus.WAITING_APPROVAL.name().equals(item.latestExecutionStatus()))
                .count();

        return new ControlDashboardOverviewResponse(
                storeId,
                traceId,
                latestSessionMap.size(),
                entries.size(),
                runningExecutionCount,
                waitingApprovalExecutionCount,
                pending,
                approved,
                rejected,
                memoryTypeBreakdown,
                memoryLayerBreakdown,
                recentApprovals,
                activeSessions
        );
    }

    private DashboardApprovalItemResponse toApprovalItem(MemoryTimelineEntry entry) {
        ApprovalTicket ticket = ApprovalTicketCodec.decode(entry.content());
        if (ticket == null) {
            return null;
        }
        return new DashboardApprovalItemResponse(
                ticket.ticketId(),
                asString(entry.metadata().get("sessionId")),
                ticket.status().name(),
                asString(ticket.payload().get("targetAgent")),
                ticket.requestedBy(),
                ticket.decidedBy(),
                ticket.createdAt(),
                ticket.decidedAt()
        );
    }

    private DashboardSessionItemResponse toSessionItem(MemoryTimelineEntry entry) {
        String sessionId = asString(entry.metadata().get("sessionId"));
        return new DashboardSessionItemResponse(
                sessionId,
                entry.memoryType(),
                entry.traceId(),
                entry.createdAt(),
                1,
                null,
                null
        );
    }

    private DashboardSessionItemResponse pickLatest(DashboardSessionItemResponse left, DashboardSessionItemResponse right) {
        DashboardSessionItemResponse latest = left.lastUpdatedAt() != null
                && right.lastUpdatedAt() != null
                && left.lastUpdatedAt().isAfter(right.lastUpdatedAt()) ? left : right;
        return new DashboardSessionItemResponse(
                latest.sessionId(),
                latest.lastMemoryType(),
                latest.lastTraceId(),
                latest.lastUpdatedAt(),
                left.eventCount() + right.eventCount(),
                latest.latestExecutionId(),
                latest.latestExecutionStatus()
        );
    }

    private DashboardApprovalItemResponse pickLatestApproval(DashboardApprovalItemResponse left, DashboardApprovalItemResponse right) {
        if (left.decidedAt() == null && right.decidedAt() == null) {
            return left.createdAt() != null && right.createdAt() != null && left.createdAt().isAfter(right.createdAt()) ? left : right;
        }
        if (left.decidedAt() == null) {
            return right;
        }
        if (right.decidedAt() == null) {
            return left;
        }
        return left.decidedAt().isAfter(right.decidedAt()) ? left : right;
    }

    private DashboardSessionItemResponse enrichWithExecution(String storeId,
                                                             DashboardSessionItemResponse item,
                                                             String traceId) {
        ExecutionGraphViewResult latestExecution = getLatestExecution(storeId, item.sessionId(), traceId);
        return new DashboardSessionItemResponse(
                item.sessionId(),
                item.lastMemoryType(),
                item.lastTraceId(),
                item.lastUpdatedAt(),
                item.eventCount(),
                latestExecution != null ? latestExecution.executionId() : null,
                latestExecution != null && latestExecution.overallStatus() != null ? latestExecution.overallStatus().name() : null
        );
    }

    private String asString(Object value) {
        return value instanceof String text ? text : null;
    }

    private List<MemoryLayer> parseLayers(String layers) {
        if (layers == null || layers.isBlank()) {
            return List.of();
        }
        return java.util.Arrays.stream(layers.split(","))
                .map(String::trim)
                .filter(text -> !text.isBlank())
                .map(String::toUpperCase)
                .map(MemoryLayer::valueOf)
                .toList();
    }
}
