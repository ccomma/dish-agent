package com.example.dish.memory.service.impl;

import com.example.dish.common.runtime.ApprovalTicketStatus;
import com.example.dish.control.approval.model.ApprovalDecisionAction;
import com.example.dish.control.approval.model.ApprovalTicketCreateRequest;
import com.example.dish.control.approval.model.ApprovalTicketDecisionRequest;
import com.example.dish.control.approval.model.ApprovalTicketQueryRequest;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class ApprovalTicketServiceImplTest {

    @BeforeEach
    void setUp() {
        MemoryReadServiceImpl.clearForTest();
        ApprovalTicketServiceImpl.clearForTest();
    }

    @AfterEach
    void tearDown() {
        MemoryReadServiceImpl.clearForTest();
        ApprovalTicketServiceImpl.clearForTest();
    }

    @Test
    void shouldCreateQueryAndApproveTicket() {
        ApprovalTicketServiceImpl service = new ApprovalTicketServiceImpl();

        var created = service.create(new ApprovalTicketCreateRequest(
                "APR-5001",
                "STORE-5",
                "SESSION-5",
                "trace-5001",
                "step-1",
                "work-order",
                "CREATE_REFUND",
                "plan-5001",
                "gateway",
                "need approval"
        ));

        Assertions.assertTrue(created.success());
        Assertions.assertEquals(ApprovalTicketStatus.PENDING, created.ticket().status());

        var queried = service.get(new ApprovalTicketQueryRequest("APR-5001", "STORE-5", "SESSION-5", "trace-query"));
        Assertions.assertTrue(queried.found());
        Assertions.assertEquals(ApprovalTicketStatus.PENDING, queried.ticket().status());

        var decided = service.decide(new ApprovalTicketDecisionRequest(
                "APR-5001",
                "STORE-5",
                "SESSION-5",
                "trace-5002",
                ApprovalDecisionAction.APPROVE,
                "ops-user",
                "approved"
        ));

        Assertions.assertTrue(decided.success());
        Assertions.assertEquals(ApprovalTicketStatus.APPROVED, decided.ticket().status());
        Assertions.assertEquals("ops-user", decided.ticket().decidedBy());
    }
}
