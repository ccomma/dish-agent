package com.example.dish.gateway.service.impl;

import com.example.dish.common.runtime.ApprovalTicket;
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
import com.example.dish.control.memory.model.MemoryTimelineRequest;
import com.example.dish.control.memory.model.MemoryTimelineResult;
import com.example.dish.control.memory.service.MemoryReadService;
import com.example.dish.control.memory.service.MemoryTimelineService;
import com.example.dish.gateway.dto.control.ApprovalDecisionResponse;
import com.example.dish.gateway.dto.control.ApprovalTicketViewResponse;
import com.example.dish.gateway.dto.control.ControlDashboardOverviewResponse;
import com.example.dish.gateway.dto.control.DashboardSessionItemResponse;
import com.example.dish.gateway.dto.control.SessionMemoryRetrievalResponse;
import com.example.dish.gateway.dto.control.SessionMemoryTimelineResponse;
import com.example.dish.gateway.observability.ExecutionMetricsService;
import com.example.dish.gateway.service.ControlPlaneQueryService;
import com.example.dish.gateway.service.ExecutionResumeService;
import com.example.dish.gateway.support.ControlPlaneViewAssembler;
import com.example.dish.gateway.support.DashboardOverviewAssembler;
import org.apache.dubbo.config.annotation.DubboReference;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import javax.annotation.Resource;
import java.util.List;
import java.util.Map;

/**
 * 控制面查询服务门面。
 * 负责把 memory / approval / execution runtime 的 Dubbo 查询结果转换成 gateway 控制台 DTO。
 */
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
    @Resource
    private DashboardOverviewAssembler dashboardOverviewAssembler;
    @Resource
    private ControlPlaneViewAssembler controlPlaneViewAssembler;

    @Override
    public SessionMemoryTimelineResponse getSessionTimeline(String storeId,
                                                            String sessionId,
                                                            String memoryType,
                                                            String keyword,
                                                            int limit,
                                                            String traceId) {
        // 1. 先向 memory timeline 服务查询会话时间线。
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

        // 2. 返回带 source 和 total 的控制台响应。
        return controlPlaneViewAssembler.toTimelineResponse(storeId, sessionId, result);
    }

    @Override
    public SessionMemoryRetrievalResponse retrieveSessionMemory(String storeId,
                                                               String sessionId,
                                                               String query,
                                                               String layers,
                                                               int limit,
                                                               String traceId) {
        // 1. 读取语义召回结果。
        MemoryReadResult result = memoryReadService.read(new MemoryReadRequest(
                storeId,
                sessionId,
                query,
                parseLayers(layers),
                limit > 0 ? limit : 5,
                traceId
        ));

        // 2. 转成控制台可直接展示的 hit 结构。
        return controlPlaneViewAssembler.toRetrievalResponse(storeId, sessionId, result);
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
        // 审批票据查不到时返回显式 NOT_FOUND，避免前端再猜空值语义。
        ApprovalTicketQueryResult result = approvalTicketService.get(new ApprovalTicketQueryRequest(
                approvalId,
                storeId,
                sessionId,
                traceId
        ));
        if (result == null || !result.found() || result.ticket() == null) {
            return controlPlaneViewAssembler.notFoundApproval(storeId, sessionId, approvalId, traceId);
        }

        ApprovalTicket ticket = result.ticket();
        // 命中后补齐审批状态、targetAgent、trace 等展示字段。
        return controlPlaneViewAssembler.toApprovalView(storeId, sessionId, traceId, ticket);
    }

    @Override
    public ApprovalDecisionResponse decideApprovalTicket(String storeId,
                                                         String sessionId,
                                                         String approvalId,
                                                         ApprovalDecisionAction action,
                                                         String decidedBy,
                                                         String decisionReason,
                                                         String traceId) {
        // 1. 先把审批动作下发给 approval 服务。
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
                // 2. 审批通过则恢复 execution，拒绝则取消剩余步骤。
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
        // 3. 无论成功与否都记录审批指标，并返回控制台响应。
        executionMetricsService.recordApprovalDecision(action, result != null && result.success());

        return controlPlaneViewAssembler.toApprovalDecisionResponse(
                storeId, sessionId, approvalId, traceId, decidedBy, decisionReason, result);
    }

    @Override
    public ControlDashboardOverviewResponse getDashboardOverview(String storeId, int limit, String traceId) {
        // 1. 按租户维度拉取较大的时间线窗口，作为 dashboard 聚合基础数据。
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

        // 2. 服务类只补齐 execution runtime 查询能力，具体 dashboard 聚合交给 assembler。
        return dashboardOverviewAssembler.assemble(
                storeId,
                traceId,
                result.entries(),
                limit,
                item -> enrichWithExecution(storeId, item, traceId)
        );
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
