package com.example.dish.memory.storage;

import com.example.dish.common.runtime.ApprovalTicket;
import com.example.dish.control.support.ApprovalTicketCodec;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Component
/**
 * 审批票据存储适配层。
 * 这里统一屏蔽 Redis 模式和本地内存模式的差异，让审批服务只关心保存和读取票据。
 */
public class ApprovalTicketStorage {

    private static final Map<String, ApprovalTicket> TICKETS = new ConcurrentHashMap<>();

    @Autowired
    private MemoryEntryStorage memoryEntryStorage;

    public void save(String tenantId, String sessionId, ApprovalTicket ticket) {
        // 1. Redis 模式下把审批票据序列化后交给时间线存储统一管理。
        if (memoryEntryStorage.usesRedis()) {
            memoryEntryStorage.saveApproval(tenantId, sessionId, ticket.ticketId(), ApprovalTicketCodec.encode(ticket));
            return;
        }
        // 2. 本地模式下直接落到进程内 Map，便于测试和 bootstrap 场景使用。
        TICKETS.put(ticketKey(tenantId, sessionId, ticket.ticketId()), ticket);
    }

    public ApprovalTicket find(String tenantId, String sessionId, String approvalId) {
        // 1. Redis 模式先取原始字符串，再通过稳定 codec 还原审批票据。
        if (memoryEntryStorage.usesRedis()) {
            return ApprovalTicketCodec.decode(memoryEntryStorage.loadApproval(tenantId, sessionId, approvalId));
        }
        // 2. 本地模式直接按复合 key 命中进程内缓存。
        return TICKETS.get(ticketKey(tenantId, sessionId, approvalId));
    }

    public static void clearForTest() {
        TICKETS.clear();
    }

    private static String ticketKey(String tenantId, String sessionId, String approvalId) {
        return tenantId + "::" + sessionId + "::" + approvalId;
    }
}
