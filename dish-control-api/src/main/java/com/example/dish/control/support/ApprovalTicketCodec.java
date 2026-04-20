package com.example.dish.control.support;

import com.example.dish.common.runtime.ApprovalTicket;
import com.example.dish.common.runtime.ApprovalTicketStatus;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;

public final class ApprovalTicketCodec {

    private ApprovalTicketCodec() {
    }

    public static String encode(ApprovalTicket ticket) {
        Map<String, String> fields = new LinkedHashMap<>();
        put(fields, "ticketId", ticket.ticketId());
        put(fields, "executionId", ticket.executionId());
        put(fields, "nodeId", ticket.nodeId());
        put(fields, "status", ticket.status() != null ? ticket.status().name() : null);
        put(fields, "requestedBy", ticket.requestedBy());
        put(fields, "decidedBy", ticket.decidedBy());
        put(fields, "decisionReason", ticket.decisionReason());
        put(fields, "createdAt", ticket.createdAt() != null ? ticket.createdAt().toString() : null);
        put(fields, "decidedAt", ticket.decidedAt() != null ? ticket.decidedAt().toString() : null);
        put(fields, "targetAgent", asString(ticket.payload().get("targetAgent")));
        put(fields, "intent", asString(ticket.payload().get("intent")));
        put(fields, "sessionId", asString(ticket.payload().get("sessionId")));
        put(fields, "storeId", asString(ticket.payload().get("storeId")));
        put(fields, "planId", asString(ticket.payload().get("planId")));
        put(fields, "traceId", asString(ticket.metadata().get("traceId")));

        StringBuilder builder = new StringBuilder();
        for (Map.Entry<String, String> entry : fields.entrySet()) {
            if (builder.length() > 0) {
                builder.append('\n');
            }
            builder.append(entry.getKey()).append('=').append(escape(entry.getValue()));
        }
        return builder.toString();
    }

    public static ApprovalTicket decode(String payload) {
        if (payload == null || payload.isBlank()) {
            return null;
        }

        Map<String, String> fields = new LinkedHashMap<>();
        for (String line : payload.split("\\n")) {
            int idx = line.indexOf('=');
            if (idx <= 0) {
                continue;
            }
            fields.put(line.substring(0, idx), unescape(line.substring(idx + 1)));
        }

        String ticketId = fields.get("ticketId");
        if (ticketId == null || ticketId.isBlank()) {
            return null;
        }

        return new ApprovalTicket(
                ticketId,
                fields.get("executionId"),
                fields.get("nodeId"),
                parseStatus(fields.get("status")),
                fields.get("requestedBy"),
                fields.get("decidedBy"),
                fields.get("decisionReason"),
                parseInstant(fields.get("createdAt")),
                parseInstant(fields.get("decidedAt")),
                mapOf(
                        "targetAgent", fields.get("targetAgent"),
                        "intent", fields.get("intent"),
                        "sessionId", fields.get("sessionId"),
                        "storeId", fields.get("storeId"),
                        "planId", fields.get("planId")
                ),
                mapOf("traceId", fields.get("traceId"))
        );
    }

    private static void put(Map<String, String> fields, String key, String value) {
        if (value != null) {
            fields.put(key, value);
        }
    }

    private static String asString(Object value) {
        return value instanceof String text ? text : null;
    }

    private static String escape(String value) {
        return value == null ? "" : value.replace("\\", "\\\\").replace("\n", "\\n").replace("=", "\\=");
    }

    private static String unescape(String value) {
        StringBuilder builder = new StringBuilder();
        boolean escaping = false;
        for (int i = 0; i < value.length(); i++) {
            char current = value.charAt(i);
            if (escaping) {
                builder.append(current == 'n' ? '\n' : current);
                escaping = false;
                continue;
            }
            if (current == '\\') {
                escaping = true;
                continue;
            }
            builder.append(current);
        }
        return builder.toString();
    }

    private static ApprovalTicketStatus parseStatus(String value) {
        if (value == null || value.isBlank()) {
            return ApprovalTicketStatus.PENDING;
        }
        return ApprovalTicketStatus.valueOf(value);
    }

    private static Instant parseInstant(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        return Instant.parse(value);
    }

    private static Map<String, Object> mapOf(Object... keyValues) {
        Map<String, Object> values = new LinkedHashMap<>();
        for (int i = 0; i < keyValues.length - 1; i += 2) {
            Object key = keyValues[i];
            Object value = keyValues[i + 1];
            if (key instanceof String text && value != null) {
                values.put(text, value);
            }
        }
        return values;
    }
}
