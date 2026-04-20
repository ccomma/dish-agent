package com.example.dish.gateway.controller;

import com.example.dish.common.contract.RoutingDecision;
import com.example.dish.gateway.agent.RoutingAgent;
import com.example.dish.control.approval.model.ApprovalDecisionAction;
import com.example.dish.gateway.dto.control.ApprovalDecisionResponse;
import com.example.dish.gateway.dto.control.ApprovalTicketViewResponse;
import com.example.dish.gateway.dto.control.ControlDashboardOverviewResponse;
import com.example.dish.gateway.dto.control.PlanPreviewResponse;
import com.example.dish.gateway.dto.control.SessionMemoryTimelineResponse;
import com.example.dish.gateway.service.ControlPlaneQueryService;
import com.example.dish.gateway.service.OrchestrationControlService;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import javax.annotation.Resource;
import java.util.UUID;

import static com.example.dish.gateway.config.TraceIdFilter.TRACE_HEADER;

@RestController
@RequestMapping("/api/control")
public class ControlPlaneController {

    private static final String STORE_HEADER = "X-Store-Id";

    @Resource
    private RoutingAgent routingAgent;
    @Resource
    private OrchestrationControlService orchestrationControlService;
    @Resource
    private ControlPlaneQueryService controlPlaneQueryService;

    @PostMapping("/plan-preview")
    public PlanPreviewResponse previewPlan(@RequestBody PlanPreviewRequest request, HttpServletRequest httpServletRequest) {
        String sessionId = request.getSessionId() != null && !request.getSessionId().isBlank()
                ? request.getSessionId()
                : "SESSION_PREVIEW_" + UUID.randomUUID().toString().substring(0, 8);
        String storeId = request.getStoreId() != null && !request.getStoreId().isBlank()
                ? request.getStoreId()
                : httpServletRequest.getHeader(STORE_HEADER);
        String traceId = resolveTraceId(httpServletRequest.getHeader(TRACE_HEADER));
        RoutingDecision routing = routingAgent.route(request.getMessage(), sessionId, storeId, null);
        return orchestrationControlService.preview(routing, traceId);
    }

    @GetMapping("/sessions/{sessionId}/memory")
    public SessionMemoryTimelineResponse sessionTimeline(@PathVariable String sessionId,
                                                         @RequestHeader(value = STORE_HEADER, required = false) String storeId,
                                                         @RequestParam(required = false) String memoryType,
                                                         @RequestParam(required = false) String keyword,
                                                         @RequestParam(required = false, defaultValue = "20") int limit,
                                                         @RequestHeader(value = TRACE_HEADER, required = false) String traceId) {
        return controlPlaneQueryService.getSessionTimeline(storeId, sessionId, memoryType, keyword, limit, resolveTraceId(traceId));
    }

    @GetMapping("/sessions/{sessionId}/approvals/{approvalId}")
    public ApprovalTicketViewResponse approvalTicket(@PathVariable String sessionId,
                                                     @PathVariable String approvalId,
                                                     @RequestHeader(value = STORE_HEADER, required = false) String storeId,
                                                     @RequestHeader(value = TRACE_HEADER, required = false) String traceId) {
        return controlPlaneQueryService.getApprovalTicket(storeId, sessionId, approvalId, resolveTraceId(traceId));
    }

    @PostMapping("/sessions/{sessionId}/approvals/{approvalId}/approve")
    public ApprovalDecisionResponse approveTicket(@PathVariable String sessionId,
                                                  @PathVariable String approvalId,
                                                  @RequestBody ApprovalDecisionRequest request,
                                                  @RequestHeader(value = STORE_HEADER, required = false) String storeId,
                                                  @RequestHeader(value = TRACE_HEADER, required = false) String traceId) {
        return controlPlaneQueryService.decideApprovalTicket(
                storeId,
                sessionId,
                approvalId,
                ApprovalDecisionAction.APPROVE,
                request.getDecidedBy(),
                request.getDecisionReason(),
                resolveTraceId(traceId)
        );
    }

    @PostMapping("/sessions/{sessionId}/approvals/{approvalId}/reject")
    public ApprovalDecisionResponse rejectTicket(@PathVariable String sessionId,
                                                 @PathVariable String approvalId,
                                                 @RequestBody ApprovalDecisionRequest request,
                                                 @RequestHeader(value = STORE_HEADER, required = false) String storeId,
                                                 @RequestHeader(value = TRACE_HEADER, required = false) String traceId) {
        return controlPlaneQueryService.decideApprovalTicket(
                storeId,
                sessionId,
                approvalId,
                ApprovalDecisionAction.REJECT,
                request.getDecidedBy(),
                request.getDecisionReason(),
                resolveTraceId(traceId)
        );
    }

    @GetMapping("/dashboard/overview")
    public ControlDashboardOverviewResponse dashboardOverview(@RequestHeader(value = STORE_HEADER, required = false) String storeId,
                                                              @RequestParam(required = false, defaultValue = "10") int limit,
                                                              @RequestHeader(value = TRACE_HEADER, required = false) String traceId) {
        return controlPlaneQueryService.getDashboardOverview(storeId, limit, resolveTraceId(traceId));
    }

    private String resolveTraceId(String traceId) {
        if (traceId != null && !traceId.isBlank()) {
            return traceId;
        }
        return "TRC-CTRL-" + UUID.randomUUID().toString().substring(0, 8);
    }

    public static class PlanPreviewRequest {
        private String message;
        private String sessionId;
        private String storeId;

        public String getMessage() {
            return message;
        }

        public void setMessage(String message) {
            this.message = message;
        }

        public String getSessionId() {
            return sessionId;
        }

        public void setSessionId(String sessionId) {
            this.sessionId = sessionId;
        }

        public String getStoreId() {
            return storeId;
        }

        public void setStoreId(String storeId) {
            this.storeId = storeId;
        }
    }

    public static class ApprovalDecisionRequest {
        private String decidedBy;
        private String decisionReason;

        public String getDecidedBy() {
            return decidedBy;
        }

        public void setDecidedBy(String decidedBy) {
            this.decidedBy = decidedBy;
        }

        public String getDecisionReason() {
            return decisionReason;
        }

        public void setDecisionReason(String decisionReason) {
            this.decisionReason = decisionReason;
        }
    }
}
