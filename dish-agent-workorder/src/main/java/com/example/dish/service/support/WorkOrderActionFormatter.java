package com.example.dish.service.support;

import com.example.dish.common.context.AgentContext;
import com.example.dish.tools.InventoryResult;
import com.example.dish.tools.InventoryTools;
import com.example.dish.tools.OrderResult;
import com.example.dish.tools.OrderTools;
import com.example.dish.tools.RefundResult;
import com.example.dish.tools.RefundTools;
import org.springframework.stereotype.Component;

import javax.annotation.Resource;

/**
 * 工单动作结果格式化器。
 * 负责调用底层工具并把结果整理成最终面向用户的文本，避免 ReAct 引擎同时承担工具调用和文案拼装职责。
 */
@Component
public class WorkOrderActionFormatter {

    @Resource
    private InventoryTools inventoryTools;
    @Resource
    private OrderTools orderTools;
    @Resource
    private RefundTools refundTools;

    public String formatInventory(AgentContext context) {
        // 1. 根据是否指定 dishName 选择查询单品库存或全量库存。
        InventoryResult result;
        if (context.getDishName() != null && !context.getDishName().isEmpty()) {
            result = inventoryTools.queryInventory(context.getStoreId(), context.getDishName());
        } else {
            result = inventoryTools.queryAllInventory(context.getStoreId());
        }

        // 2. 失败时返回统一失败文案。
        if (!result.isSuccess()) {
            return "查询失败：" + result.getErrorMessage();
        }

        // 3. 成功时格式化库存清单，供最终回答直接展示。
        StringBuilder sb = new StringBuilder();
        sb.append("【库存查询结果】\n");
        sb.append("门店：").append(result.getStoreId()).append("\n\n");
        for (InventoryResult.InventoryItem item : result.getItems()) {
            sb.append("• ").append(item.getDishName())
                    .append(" - ").append(item.getQuantity()).append("份")
                    .append(" (").append(item.getStatus()).append(")\n");
        }
        return sb.toString();
    }

    public String formatOrder(AgentContext context) {
        OrderResult result = orderTools.queryOrderStatus(context.getOrderId());
        if (!result.isSuccess()) {
            return "查询失败：" + result.getErrorMessage();
        }
        return "【订单查询结果】\n" +
                "订单号：" + result.getOrderId() + "\n" +
                "门店：" + result.getStoreId() + "\n" +
                "商品：" + result.getItems() + "\n" +
                "状态：" + result.getStatus() + "\n";
    }

    public String formatRefund(AgentContext context) {
        RefundResult result = refundTools.createRefundTicket(
                context.getOrderId(),
                context.getRefundReason() != null ? context.getRefundReason() : "用户主动申请"
        );
        if (!result.isSuccess()) {
            return "退款申请失败：" + result.getErrorMessage();
        }
        return "【退款工单创建成功】\n" +
                "工单号：" + result.getTicketId() + "\n" +
                "订单号：" + result.getOrderId() + "\n" +
                "原因：" + result.getReason() + "\n" +
                "状态：" + result.getStatus() + "\n" +
                "创建时间：" + result.getCreateTime() + "\n\n" +
                "我们将尽快处理您的退款申请，请保持手机畅通。";
    }
}
