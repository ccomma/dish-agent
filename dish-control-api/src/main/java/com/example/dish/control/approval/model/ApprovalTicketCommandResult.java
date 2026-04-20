package com.example.dish.control.approval.model;

import com.example.dish.common.runtime.ApprovalTicket;

import java.io.Serializable;

public record ApprovalTicketCommandResult(
        boolean success,
        ApprovalTicket ticket,
        String message
) implements Serializable {
}
