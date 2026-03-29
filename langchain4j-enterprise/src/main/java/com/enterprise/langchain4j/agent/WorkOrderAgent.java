package com.enterprise.langchain4j.agent;

import com.enterprise.langchain4j.Config;
import com.enterprise.langchain4j.IntentType;
import com.enterprise.langchain4j.context.AgentContext;
import com.enterprise.langchain4j.contract.AgentResponse;
import com.enterprise.langchain4j.tool.InventoryTools;
import com.enterprise.langchain4j.tool.OrderTools;
import com.enterprise.langchain4j.tool.RefundTools;
import dev.langchain4j.memory.chat.MessageWindowChatMemory;
import dev.langchain4j.model.chat.ChatLanguageModel;
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

        ChatLanguageModel chatModel = OpenAiChatModel.builder()
                .apiKey(config.getApiKey())
                .baseUrl(config.getBaseUrl())
                .modelName(config.getModel())
                .temperature(0.3)
                .build();

        this.inventoryTools = new InventoryTools();
        this.orderTools = new OrderTools();
        this.refundTools = new RefundTools();

        this.assistant = AiServices.builder(WorkOrderAssistant.class)
                .chatLanguageModel(chatModel)
                .chatMemory(MessageWindowChatMemory.withMaxMessages(20))
                .tools(inventoryTools, orderTools, refundTools)
                .build();
    }

    /**
     * 处理工单请求
     */
    public AgentResponse process(AgentContext context) {
        IntentType intent = context.getIntent();

        // 根据意图类型调用对应工具
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
        String storeId = context.getStoreId() != null ? context.getStoreId() : "STORE_001";
        String dishName = context.getDishName();

        String result;
        if (dishName != null && !dishName.isEmpty()) {
            result = inventoryTools.queryInventory(storeId, dishName);
        } else {
            result = inventoryTools.queryAllInventory(storeId);
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
     * 处理订单查询
     */
    private AgentResponse handleOrderQuery(AgentContext context) {
        String orderId = context.getOrderId();
        if (orderId == null || orderId.isEmpty()) {
            return AgentResponse.failure(
                    "请提供订单号，例如：查询订单12345的状态",
                    "WorkOrderAgent",
                    context
            );
        }

        String result = orderTools.queryOrderStatus(orderId);
        return AgentResponse.success(result, "WorkOrderAgent", context);
    }

    /**
     * 处理退款申请
     */
    private AgentResponse handleRefundCreation(AgentContext context) {
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

        String result = refundTools.createRefundTicket(orderId, reason);
        return AgentResponse.success(result, "WorkOrderAgent", context);
    }
}
