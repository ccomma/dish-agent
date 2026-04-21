package com.example.dish.gateway.service;

import com.example.dish.control.approval.model.ApprovalDecisionAction;
import com.example.dish.control.execution.model.ExecutionGraphViewResult;
import com.example.dish.control.execution.model.ExecutionReplayResult;
import com.example.dish.gateway.dto.control.ApprovalDecisionResponse;
import com.example.dish.gateway.dto.control.ApprovalTicketViewResponse;
import com.example.dish.gateway.dto.control.ControlDashboardOverviewResponse;
import com.example.dish.gateway.dto.control.SessionMemoryRetrievalResponse;
import com.example.dish.gateway.dto.control.SessionMemoryTimelineResponse;

public interface ControlPlaneQueryService {

    SessionMemoryTimelineResponse getSessionTimeline(String storeId, String sessionId, String memoryType, String keyword, int limit, String traceId);

    SessionMemoryRetrievalResponse retrieveSessionMemory(String storeId, String sessionId, String query, String layers, int limit, String traceId);

    ExecutionGraphViewResult getLatestExecution(String storeId, String sessionId, String traceId);

    ExecutionGraphViewResult getExecutionGraph(String storeId, String executionId, String traceId);

    ExecutionReplayResult getExecutionReplay(String storeId, String executionId, String traceId);

    ApprovalTicketViewResponse getApprovalTicket(String storeId, String sessionId, String approvalId, String traceId);

    ApprovalDecisionResponse decideApprovalTicket(String storeId, String sessionId, String approvalId, ApprovalDecisionAction action, String decidedBy, String decisionReason, String traceId);

    ControlDashboardOverviewResponse getDashboardOverview(String storeId, int limit, String traceId);
}
