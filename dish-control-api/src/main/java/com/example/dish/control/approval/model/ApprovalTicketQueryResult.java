package com.example.dish.control.approval.model;

import com.example.dish.common.runtime.ApprovalTicket;

import java.io.Serializable;

public record ApprovalTicketQueryResult(
        boolean found,
        ApprovalTicket ticket,
        String message
) implements Serializable {
}
