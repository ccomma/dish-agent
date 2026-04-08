package com.enterprise.langchain4j.agent;

import com.enterprise.langchain4j.Config;
import com.enterprise.langchain4j.context.AgentContext;
import com.enterprise.langchain4j.contract.AgentResponse;
import com.enterprise.langchain4j.contract.RoutingDecision;
import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.model.openai.OpenAiChatModel;

/**
 * 编排Agent（Orchestration/Supervisor）
 *
 * 职责边界：
 * 1. 作为系统唯一入口，接收用户输入
 * 2. 调用RoutingAgent获取路由决策
 * 3. 根据路由决策分发到对应的专业Agent
 * 4. 处理Agent间的上下文流转
 * 5. 组装最终响应返回用户
 *
 * 这是多Agent协作的核心协调者
 */
public class OrchestrationAgent {

    private final RoutingAgent routingAgent;
    private final DishKnowledgeAgent dishKnowledgeAgent;
    private final WorkOrderAgent workOrderAgent;
    private final ChatModel chatModel;

    public OrchestrationAgent() {
        Config config = Config.getInstance();

        this.chatModel = OpenAiChatModel.builder()
                .apiKey(config.getApiKey())
                .baseUrl(config.getBaseUrl())
                .modelName(config.getModel())
                .temperature(0.7)
                .build();

        this.routingAgent = new RoutingAgent();
        this.dishKnowledgeAgent = new DishKnowledgeAgent();
        this.workOrderAgent = new WorkOrderAgent();
    }

    /**
     * 处理用户输入的主入口
     *
     * 流程：
     * 1. 创建初始上下文（包含用户输入）
     * 2. 调用RoutingAgent获取路由决策
     * 3. 根据路由决策分发到对应Agent
     * 4. 收集响应，可能需要进一步处理
     * 5. 返回最终响应
     */
    public String process(String userInput) {
        // 1. 创建初始上下文
        AgentContext initialContext = AgentContext.builder()
                .userInput(userInput)
                .sessionId(generateSessionId())
                .build();

        // 2. 路由决策
        RoutingDecision routing = routingAgent.route(userInput, initialContext);
        System.out.println("[编排] 路由决策: " + routing.targetAgent()
                + " | 意图: " + routing.intent().name());

        // 3. 分发到对应Agent
        AgentResponse response = dispatch(routing);

        // 4. 返回响应内容
        return response.format();
    }

    /**
     * 根据路由决策分发到对应Agent
     */
    private AgentResponse dispatch(RoutingDecision routing) {
        AgentContext context = routing.context();

        return switch (routing.targetAgent()) {
            case RoutingDecision.TARGET_DISH_KNOWLEDGE -> {
                // 为菜品知识Agent补充上下文
                AgentContext updatedContext = AgentContext.builder()
                        .sessionId(context.getSessionId())
                        .intent(context.getIntent())
                        .storeId(context.getStoreId())
                        .orderId(context.getOrderId())
                        .dishName(context.getDishName())
                        .userInput(context.getUserInput())
                        .build();
                yield dishKnowledgeAgent.answer(context.getUserInput(), updatedContext);
            }

            case RoutingDecision.TARGET_WORK_ORDER -> workOrderAgent.process(context);

            case RoutingDecision.TARGET_CHAT -> handleChat(context);

            default -> AgentResponse.failure(
                    "无法处理该请求",
                    "OrchestrationAgent",
                    context
            );
        };
    }

    /**
     * 处理闲聊类请求（直接对话）
     */
    private AgentResponse handleChat(AgentContext context) {
        String response = chatModel.chat(context.getUserInput());
        return AgentResponse.success(response, "ChatAgent", context);
    }

    private String generateSessionId() {
        return "SESSION_" + System.currentTimeMillis();
    }

    /**
     * 交互式对话入口
     */
    public void chat() {
        System.out.println("╔══════════════════════════════════════════╗");
        System.out.println("║     餐饮智能助手 - 多Agent协同架构      ║");
        System.out.println("╠══════════════════════════════════════════╣");
        System.out.println("║  功能：菜品咨询 | 库存查询 | 订单查询    ║");
        System.out.println("║  输入 'exit' 退出                        ║");
        System.out.println("╚══════════════════════════════════════════╝");
        System.out.println();

        java.util.Scanner scanner = new java.util.Scanner(System.in);

        while (true) {
            System.out.print("【用户】 ");
            String userInput = scanner.nextLine().trim();

            if (userInput.equalsIgnoreCase("exit")) {
                System.out.println("\n感谢使用，再见！");
                break;
            }

            if (userInput.isEmpty()) continue;

            long startTime = System.currentTimeMillis();

            try {
                String response = process(userInput);
                long duration = System.currentTimeMillis() - startTime;

                System.out.println("\n【助手】 " + response);
                System.out.println("─── [响应时间: " + duration + "ms] ───\n");
            } catch (Exception e) {
                System.err.println("处理出错: " + e.getMessage());
                e.printStackTrace();
            }
        }

        scanner.close();
    }

    public static void main(String[] args) {
        OrchestrationAgent agent = new OrchestrationAgent();
        agent.chat();
    }
}
