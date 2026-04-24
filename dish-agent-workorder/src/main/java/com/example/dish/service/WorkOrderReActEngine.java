package com.example.dish.service;

import com.example.dish.common.agent.ReActState;
import com.example.dish.common.context.AgentContext;
import com.example.dish.common.react.AbstractReActEngine;
import com.example.dish.common.react.ReActEngine;
import com.example.dish.service.support.WorkOrderActionFormatter;
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
    private WorkOrderActionFormatter workOrderActionFormatter;

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
        // 根据意图决定本轮应该调用哪个业务工具。
        return switch (context.getIntent()) {
            case QUERY_INVENTORY -> "识别为库存查询，调用库存工具";
            case QUERY_ORDER -> "识别为订单查询，调用订单工具";
            case CREATE_REFUND -> "识别为退款申请，调用退款工具";
            default -> "无法识别业务意图，返回失败";
        };
    }

    @Override
    protected Optional<Action> decideAction(ReActState state, AgentContext context) {
        // 1. 工单类问题只需要一次工具调用，已经执行过 action 就直接停止。
        boolean hasAction = state.getSteps().stream().anyMatch(step -> step.getType() == ReActState.StepType.ACTION);
        if (hasAction) {
            return Optional.empty();
        }

        // 2. 按意图生成具体工具动作。
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
        // 执行动作时直接把工具结果格式化成终态 observation，不再进入多轮推理。
        return switch (action.type()) {
            case "query_inventory" -> ObservationResult.terminal(workOrderActionFormatter.formatInventory(context));
            case "query_order" -> ObservationResult.terminal(workOrderActionFormatter.formatOrder(context));
            case "create_refund" -> ObservationResult.terminal(workOrderActionFormatter.formatRefund(context));
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
