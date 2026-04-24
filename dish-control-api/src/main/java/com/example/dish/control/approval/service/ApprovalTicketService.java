package com.example.dish.control.approval.service;

import com.example.dish.control.approval.model.ApprovalTicketCommandResult;
import com.example.dish.control.approval.model.ApprovalTicketCreateRequest;
import com.example.dish.control.approval.model.ApprovalTicketDecisionRequest;
import com.example.dish.control.approval.model.ApprovalTicketQueryRequest;
import com.example.dish.control.approval.model.ApprovalTicketQueryResult;

/**
 * 审批票据服务契约。
 */
public interface ApprovalTicketService {

    ApprovalTicketCommandResult create(ApprovalTicketCreateRequest request);

    ApprovalTicketCommandResult decide(ApprovalTicketDecisionRequest request);

    ApprovalTicketQueryResult get(ApprovalTicketQueryRequest request);
}
