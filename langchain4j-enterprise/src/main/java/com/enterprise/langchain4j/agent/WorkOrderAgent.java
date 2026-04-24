package com.enterprise.langchain4j.agent;

import com.enterprise.langchain4j.Config;
import com.enterprise.langchain4j.classifier.IntentType;
import com.enterprise.langchain4j.context.AgentContext;
import com.enterprise.langchain4j.contract.AgentResponse;
import com.enterprise.langchain4j.tool.*;
import dev.langchain4j.memory.chat.MessageWindowChatMemory;
import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.model.openai.OpenAiChatModel;
import dev.langchain4j.service.AiServices;
import dev.langchain4j.service.SystemMessage;

import java.util.List;

/**
 * 工单处理Agent
 *
 * 职责边界：
 * 1. 接收来自RoutingAgent的工单类请求
 * 2. 从AgentContext获取已抽取的参数（门店ID、订单号等）
 * 3. 调用对应的业务工具完成操作
 * 4. 返回AgentResponse给编排层
 */
public class WorkOrderAgent {

    /**
     * 工单处理助手接口
     * 封装业务工具，支持多轮对话
     */
    public interface WorkOrderAssistant {
        @SystemMessage("""
            你是一个餐饮业务助手，负责处理库存查询、订单查询和退款申请。

            可用操作：
            - 查询门店菜品库存
            - 查询订单状态
            - 创建退款工单

            当用户询问业务问题时，使用提供的工具获取信息。
            对于退款申请，确认订单号和原因后创建工单。
            """)
        String chat(String userMessage);
    }

    private final WorkOrderAssistant assistant;
    private final InventoryTools inventoryTools;
    private final OrderTools orderTools;
    private final RefundTools refundTools;

    public WorkOrderAgent() {
        Config config = Config.getInstance();

        ChatModel chatModel = OpenAiChatModel.builder()
                .apiKey(config.getApiKey())
                .baseUrl(config.getBaseUrl())
                .modelName(config.getModel())
                .temperature(0.3)
                .build();

        this.inventoryTools = new InventoryTools();
        this.orderTools = new OrderTools();
        this.refundTools = new RefundTools();

        this.assistant = AiServices.builder(WorkOrderAssistant.class)
                .chatModel(chatModel)
                .chatMemory(MessageWindowChatMemory.withMaxMessages(20))
                .tools(inventoryTools, orderTools, refundTools)
                .build();
    }

    /**
     * 处理工单请求
     */
    public AgentResponse process(AgentContext context) {
        IntentType intent = context.getIntent();

        // 根据意图类型调用对应工具，教学版仍保持单动作直达。
        return switch (intent) {
            case QUERY_INVENTORY -> handleInventoryQuery(context);
            case QUERY_ORDER -> handleOrderQuery(context);
            case CREATE_REFUND -> handleRefundCreation(context);
            default -> AgentResponse.failure("无法处理的意图类型: " + intent, "WorkOrderAgent", context);
        };
    }

    /**
     * 处理库存查询
     */
    private AgentResponse handleInventoryQuery(AgentContext context) {
        // 1. 先归一化门店和菜品参数。
        String storeId = context.getStoreId() != null ? context.getStoreId() : "STORE_001";
        String dishName = context.getDishName();

        // 2. 再根据是否指定菜品决定查单品还是全量库存。
        String result;
        if (dishName != null && !dishName.isEmpty()) {
            result = formatInventoryResult(inventoryTools.queryInventory(storeId, dishName));
        } else {
            result = formatInventoryResult(inventoryTools.queryAllInventory(storeId));
        }

        return AgentResponse.builder()
                .success(true)
                .content(result)
                .agentName("WorkOrderAgent")
                .context(context)
                .followUpHints(List.of(
                        "需要帮您下单吗",
                        "想了解其他菜品的库存吗"
                ))
                .build();
    }

    /**
     * 格式化库存结果
     */
    private String formatInventoryResult(InventoryResult result) {
        if (!result.isSuccess()) {
            return result.getErrorMessage();
        }

        StringBuilder sb = new StringBuilder();
        sb.append("【门店 ").append(result.getStoreId()).append(" 库存情况】\n\n");

        for (InventoryResult.InventoryItem item : result.getItems()) {
            sb.append("• ").append(item.getDishName())
              .append(": ").append(item.getQuantity())
              .append("份 (").append(item.getStatus()).append(")\n");
        }

        return sb.toString();
    }

    /**
     * 处理订单查询
     */
    private AgentResponse handleOrderQuery(AgentContext context) {
        // 1. 缺少订单号时直接给出引导文案。
        String orderId = context.getOrderId();
        if (orderId == null || orderId.isEmpty()) {
            return AgentResponse.failure(
                    "请提供订单号，例如：查询订单12345的状态",
                    "WorkOrderAgent",
                    context
            );
        }

        // 2. 命中订单号后调用订单工具并格式化结果。
        String result = formatOrderResult(orderTools.queryOrderStatus(orderId));
        return AgentResponse.success(result, "WorkOrderAgent", context);
    }

    /**
     * 格式化订单结果
     */
    private String formatOrderResult(OrderResult result) {
        if (!result.isSuccess()) {
            return result.getErrorMessage();
        }

        StringBuilder sb = new StringBuilder();
        sb.append("【订单查询结果】\n\n");
        sb.append("• 订单号: ").append(result.getOrderId()).append("\n");
        sb.append("• 门店: ").append(result.getStoreId()).append("\n");
        sb.append("• 商品: ").append(result.getItems()).append("\n");
        sb.append("• 状态: ").append(result.getStatus()).append("\n");
        sb.append("• 下单时间: ").append(result.getCreateTime()).append("\n");

        return sb.toString();
    }

    /**
     * 处理退款申请
     */
    private AgentResponse handleRefundCreation(AgentContext context) {
        // 1. 先校验订单号并补齐默认退款原因。
        String orderId = context.getOrderId();
        String reason = context.getRefundReason();

        if (orderId == null || orderId.isEmpty()) {
            return AgentResponse.failure(
                    "请提供订单号",
                    "WorkOrderAgent",
                    context
            );
        }

        if (reason == null || reason.isEmpty()) {
            reason = "用户主动申请退款";
        }

        // 2. 再调用退款工具建单并格式化结果。
        String result = formatRefundResult(refundTools.createRefundTicket(orderId, reason));
        return AgentResponse.success(result, "WorkOrderAgent", context);
    }

    /**
     * 格式化退款结果
     */
    private String formatRefundResult(RefundResult result) {
        if (!result.isSuccess()) {
            return result.getErrorMessage();
        }

        StringBuilder sb = new StringBuilder();
        sb.append("【退款工单创建成功】\n\n");
        sb.append("• 工单号: ").append(result.getTicketId()).append("\n");
        sb.append("• 订单号: ").append(result.getOrderId()).append("\n");
        sb.append("• 原因: ").append(result.getReason()).append("\n");
        sb.append("• 状态: ").append(result.getStatus()).append("\n");
        sb.append("• 创建时间: ").append(result.getCreateTime()).append("\n\n");
        sb.append("备注：退款将在3-7个工作日内原路返回。");

        return sb.toString();
    }
}
