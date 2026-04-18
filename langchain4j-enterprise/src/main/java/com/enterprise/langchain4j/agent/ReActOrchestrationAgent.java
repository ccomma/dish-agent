package com.enterprise.langchain4j.agent;

import com.enterprise.langchain4j.Config;
import com.enterprise.langchain4j.classifier.IntentType;
import com.enterprise.langchain4j.context.AgentContext;
import com.enterprise.langchain4j.contract.AgentResponse;
import com.enterprise.langchain4j.contract.RoutingDecision;
import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.model.openai.OpenAiChatModel;

/**
 * ReAct 模式编排Agent
 *
 * 实现完整的多步推理和行动循环：
 * Thought → Action → Observation → Thought → ... → Final
 *
 * 相比原 OrchestrationAgent：
 * 1. 支持多轮迭代思考
 * 2. 能根据中间结果决定下一步行动
 * 3. 整合多个Agent的结果
 */
public class ReActOrchestrationAgent {

    private final RoutingAgent routingAgent;
    private final DishKnowledgeAgent dishKnowledgeAgent;
    private final WorkOrderAgent workOrderAgent;
    private final ChatModel chatModel;

    public ReActOrchestrationAgent() {
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
     * 处理用户输入（ReAct 循环入口）
     */
    public String process(String userInput) {
        String sessionId = generateSessionId();

        // 1. 初始化 ReAct 状态
        ReActState state = new ReActState(sessionId, userInput);
        state.addStep(ReActState.StepType.THOUGHT,
                "接收到用户输入，开始分析意图",
                "ReActOrchestration");

        AgentContext currentContext = AgentContext.builder()
                .sessionId(sessionId)
                .userInput(userInput)
                .build();

        // 2. 执行 ReAct 循环
        while (state.shouldContinue()) {
            state.nextIteration();
            executeReActStep(state, currentContext);
        }

        // 3. 打印执行轨迹（调试用）
        state.printTrace();

        // 4. 返回最终结果
        return state.getFinalResponse();
    }

    /**
     * 执行单个 ReAct 步骤
     */
    private void executeReActStep(ReActState state, AgentContext context) {
        // 记录思考过程
        String thought = analyzeAndDecide(state, context);
        state.addStep(ReActState.StepType.THOUGHT, thought, "ReActOrchestration");

        // 决定行动
        String action = decideAction(state, context);
        if (action.startsWith("FINAL:")) {
            // 最终响应
            state.addStep(ReActState.StepType.FINAL, action.substring(6), "ReActOrchestration");
            return;
        }

        state.addStep(ReActState.StepType.ACTION, action, "ReActOrchestration");

        // 执行行动并观察结果
        String observation = executeAction(state, context, action);
        state.addStep(ReActState.StepType.OBSERVATION, observation, "ReActOrchestration");
    }

    /**
     * 思考：分析当前状态，决定是否需要继续
     */
    private String analyzeAndDecide(ReActState state, AgentContext context) {
        int iteration = state.getCurrentIteration();

        if (iteration == 1) {
            return "首次迭代，分析用户输入的意图和需求";
        }

        // 检查之前是否有失败的行动
        for (int i = state.getSteps().size() - 1; i >= 0; i--) {
            ReActState.ReActStep step = state.getSteps().get(i);
            if (step.getType() == ReActState.StepType.OBSERVATION) {
                String obs = step.getContent();
                if (obs.contains("失败") || obs.contains("无法")) {
                    return "检测到之前的行动失败，需要调整策略或提供替代方案";
                }
                if (obs.contains("完成") || obs.contains("成功")) {
                    return "检测到行动已成功完成，检查是否需要整合更多信息";
                }
            }
        }

        return "继续分析是否需要额外的行动来满足用户需求";
    }

    /**
     * 决定下一步行动
     */
    private String decideAction(ReActState state, AgentContext context) {
        // 首次迭代，进行路由决策
        if (state.getCurrentIteration() == 1) {
            return "ROUTE:" + context.getUserInput();
        }

        // 检查之前的结果
        String lastObservation = "";
        for (int i = state.getSteps().size() - 1; i >= 0; i--) {
            ReActState.ReActStep step = state.getSteps().get(i);
            if (step.getType() == ReActState.StepType.OBSERVATION) {
                lastObservation = step.getContent();
                break;
            }
        }

        // 判断是否需要多Agent协作
        // 如果是复杂的菜品问题且涉及多个方面，可能需要多步处理
        IntentType intent = context.getIntent();
        String input = context.getUserInput();

        // 复杂查询检测：包含多个主题
        boolean isComplexQuery = (input.contains("和") || input.contains("及") ||
                input.contains("以及") || input.contains("还有"));

        if (isComplexQuery && !lastObservation.contains("多")) {
            // 复杂查询可能需要多次处理
            if (state.getCurrentIteration() == 2 && intent == IntentType.DISH_QUESTION) {
                return "EXPAND:需要处理多个相关问题";
            }
        }

        // 简单查询，直接返回结果
        return "FINAL:" + lastObservation;
    }

    /**
     * 执行行动
     */
    private String executeAction(ReActState state, AgentContext context, String action) {
        if (action.startsWith("ROUTE:")) {
            String userInput = action.substring(6);
            AgentContext initialContext = AgentContext.builder()
                    .sessionId(state.getSessionId())
                    .userInput(userInput)
                    .build();

            RoutingDecision routing = routingAgent.route(userInput, initialContext);

            // 使用 with* 方法更新上下文（不可变模式）
            context = context.withIntent(routing.intent())
                           .withStoreId(routing.context().getStoreId());

            System.out.println("[ReAct] 路由决策: " + routing.targetAgent() + " | 意图: " + routing.intent());

            return executeRouting(state, context, routing);
        }

        if (action.startsWith("EXPAND:")) {
            // 扩展查询处理
            return "已整合多个相关问题，统一给出回答";
        }

        return "未知行动类型: " + action;
    }

    /**
     * 执行路由决策
     */
    private String executeRouting(ReActState state, AgentContext context, RoutingDecision routing) {
        AgentResponse response;

        switch (routing.targetAgent()) {
            case RoutingDecision.TARGET_DISH_KNOWLEDGE -> {
                AgentContext updatedContext = buildContext(context, routing.context());
                response = dishKnowledgeAgent.answerWithReflection(
                        context.getUserInput(), updatedContext, state);
                return formatResponse(response);
            }

            case RoutingDecision.TARGET_WORK_ORDER -> {
                response = workOrderAgent.process(routing.context());
                return formatResponse(response);
            }

            case RoutingDecision.TARGET_CHAT -> {
                response = handleChat(routing.context());
                return formatResponse(response);
            }

            default -> {
                return "无法处理该请求";
            }
        }
    }

    /**
     * 构建更新后的上下文
     */
    private AgentContext buildContext(AgentContext original, AgentContext fromRouting) {
        return AgentContext.builder()
                .sessionId(original.getSessionId())
                .intent(original.getIntent())
                .storeId(fromRouting.getStoreId())
                .orderId(fromRouting.getOrderId())
                .dishName(fromRouting.getDishName())
                .refundReason(fromRouting.getRefundReason())
                .userInput(original.getUserInput())
                .build();
    }

    /**
     * 处理闲聊
     */
    private AgentResponse handleChat(AgentContext context) {
        String response = chatModel.chat(context.getUserInput());
        return AgentResponse.success(response, "ChatAgent", context);
    }

    /**
     * 格式化响应
     */
    private String formatResponse(AgentResponse response) {
        if (response.isSuccess()) {
            return response.getContent();
        } else {
            return "处理失败: " + response.getContent();
        }
    }

    private String generateSessionId() {
        return "SESSION_" + System.currentTimeMillis();
    }

    /**
     * 交互式对话入口
     */
    public void chat() {
        System.out.println("╔══════════════════════════════════════════╗");
        System.out.println("║  餐饮智能助手 - ReAct 多步推理架构    ║");
        System.out.println("╠══════════════════════════════════════════╣");
        System.out.println("║  功能：菜品咨询 | 库存查询 | 订单查询    ║");
        System.out.println("║  特点：多步思考逐步推理                  ║");
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

            if (userInput.isEmpty()) {
                continue;
            }

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
        ReActOrchestrationAgent agent = new ReActOrchestrationAgent();
        agent.chat();
    }
}