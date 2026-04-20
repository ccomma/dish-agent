package com.example.dish.gateway.dto.control;

import java.util.List;
import java.util.Map;

public record ControlDashboardOverviewResponse(
        String storeId,
        String traceId,
        int totalSessions,
        int totalMemoryEntries,
        int pendingApprovalCount,
        int approvedApprovalCount,
        int rejectedApprovalCount,
        Map<String, Integer> memoryTypeBreakdown,
        List<DashboardApprovalItemResponse> recentApprovals,
        List<DashboardSessionItemResponse> activeSessions
) {
}
