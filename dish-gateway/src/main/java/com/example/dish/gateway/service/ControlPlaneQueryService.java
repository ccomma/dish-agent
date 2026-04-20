package com.example.dish.gateway.service;

import com.example.dish.control.approval.model.ApprovalDecisionAction;
import com.example.dish.gateway.dto.control.ApprovalDecisionResponse;
import com.example.dish.gateway.dto.control.ApprovalTicketViewResponse;
import com.example.dish.gateway.dto.control.ControlDashboardOverviewResponse;
import com.example.dish.gateway.dto.control.SessionMemoryTimelineResponse;

public interface ControlPlaneQueryService {

    SessionMemoryTimelineResponse getSessionTimeline(String storeId, String sessionId, String memoryType, String keyword, int limit, String traceId);

    ApprovalTicketViewResponse getApprovalTicket(String storeId, String sessionId, String approvalId, String traceId);

    ApprovalDecisionResponse decideApprovalTicket(String storeId, String sessionId, String approvalId, ApprovalDecisionAction action, String decidedBy, String decisionReason, String traceId);

    ControlDashboardOverviewResponse getDashboardOverview(String storeId, int limit, String traceId);
}
