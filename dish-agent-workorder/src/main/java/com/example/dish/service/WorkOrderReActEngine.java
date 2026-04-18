package com.example.dish.service;

import com.example.dish.common.agent.ReActState;
import com.example.dish.common.classifier.IntentType;
import com.example.dish.common.context.AgentContext;
import com.example.dish.common.react.AbstractReActEngine;
import com.example.dish.common.react.ReActEngine;
import com.example.dish.tools.InventoryResult;
import com.example.dish.tools.InventoryTools;
import com.example.dish.tools.OrderResult;
import com.example.dish.tools.OrderTools;
import com.example.dish.tools.RefundResult;
import com.example.dish.tools.RefundTools;
import org.springframework.stereotype.Component;

import javax.annotation.Resource;
import java.util.List;
import java.util.Optional;

/**
 * 工单处理 ReAct 执行器。
 */
@Component
public class WorkOrderReActEngine extends AbstractReActEngine {

    @Resource
    private InventoryTools inventoryTools;
    @Resource
    private OrderTools orderTools;
    @Resource
    private RefundTools refundTools;

    public WorkOrderReActEngine() {
        super(3);
    }

    @Override
    protected ReActState createState(String sessionId, String userInput) {
        ReActState state = new ReActState(sessionId, userInput);
        state.setMaxIterations(getMaxIterations());
        return state;
    }

    @Override
    protected String think(ReActState state, AgentContext context) {
        return switch (context.getIntent()) {
            case QUERY_INVENTORY -> "识别为库存查询，调用库存工具";
            case QUERY_ORDER -> "识别为订单查询，调用订单工具";
            case CREATE_REFUND -> "识别为退款申请，调用退款工具";
            default -> "无法识别业务意图，返回失败";
        };
    }

    @Override
    protected Optional<Action> decideAction(ReActState state, AgentContext context) {
        boolean hasAction = state.getSteps().stream().anyMatch(step -> step.getType() == ReActState.StepType.ACTION);
        if (hasAction) {
            return Optional.empty();
        }

        return switch (context.getIntent()) {
            case QUERY_INVENTORY -> Optional.of(new Action("query_inventory",
                    context.getStoreId() + "/" + context.getDishName()));
            case QUERY_ORDER -> Optional.of(new Action("query_order", context.getOrderId()));
            case CREATE_REFUND -> Optional.of(new Action("create_refund", context.getOrderId()));
            default -> Optional.empty();
        };
    }

    @Override
    protected ObservationResult executeAction(Action action, AgentContext context) {
        return switch (action.type()) {
            case "query_inventory" -> ObservationResult.terminal(formatInventory(context));
            case "query_order" -> ObservationResult.terminal(formatOrder(context));
            case "create_refund" -> ObservationResult.terminal(formatRefund(context));
            default -> ObservationResult.terminal("无法处理的操作类型: " + action.type());
        };
    }

    @Override
    protected String generateResponse(ReActState state, AgentContext context) {
        return state.getFinalResponse();
    }

    @Override
    protected ReActEngine.ReActResult buildResult(ReActState state, AgentContext context) {
        String finalText = state.getFinalResponse();
        boolean success = finalText != null && !finalText.startsWith("查询失败") && !finalText.startsWith("退款申请失败")
                && !finalText.startsWith("无法处理");
        return new ReActEngine.ReActResult(finalText, state, success);
    }

    @Override
    protected String getAgentName() {
        return "WorkOrderAgent";
    }

    private String formatInventory(AgentContext context) {
        InventoryResult result;
        if (context.getDishName() != null && !context.getDishName().isEmpty()) {
            result = inventoryTools.queryInventory(context.getStoreId(), context.getDishName());
        } else {
            result = inventoryTools.queryAllInventory(context.getStoreId());
        }

        if (!result.isSuccess()) {
            return "查询失败：" + result.getErrorMessage();
        }

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

    private String formatOrder(AgentContext context) {
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

    private String formatRefund(AgentContext context) {
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

    public List<String> inventoryHints() {
        return List.of("需要帮您下单吗？", "想了解其他菜品吗？");
    }

    public List<String> orderHints() {
        return List.of("需要申请退款吗？", "想继续下单吗？");
    }

    public List<String> refundHints() {
        return List.of("需要查询退款进度吗？", "还有其他问题吗？");
    }
}
