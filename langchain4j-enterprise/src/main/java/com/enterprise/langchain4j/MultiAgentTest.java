package com.enterprise.langchain4j;

import com.enterprise.langchain4j.agent.DishKnowledgeAgent;
import com.enterprise.langchain4j.agent.RoutingAgent;
import com.enterprise.langchain4j.agent.WorkOrderAgent;
import com.enterprise.langchain4j.classifier.IntentClassifier;
import com.enterprise.langchain4j.classifier.IntentType;
import com.enterprise.langchain4j.context.AgentContext;
import com.enterprise.langchain4j.contract.AgentResponse;
import com.enterprise.langchain4j.contract.RoutingDecision;
import com.enterprise.langchain4j.rag.RAGPipeline;
import com.enterprise.langchain4j.tool.InventoryTools;
import com.enterprise.langchain4j.tool.OrderTools;
import com.enterprise.langchain4j.tool.RefundTools;

/**
 * 多Agent协同架构测试
 * 验证各个Agent模块的功能
 */
public class MultiAgentTest {

    public static void main(String[] args) {
        System.out.println("╔══════════════════════════════════════════╗");
        System.out.println("║   餐饮智能助手 - 多Agent模块测试      ║");
        System.out.println("╚══════════════════════════════════════════╝\n");

        // 1. 测试意图分类
        testIntentClassification();

        // 2. 测试RAG问答
        testRAG();

        // 3. 测试业务工具
        testTools();

        // 4. 测试完整流程
        testFullFlow();

        System.out.println("\n=== 所有测试完成 ===");
    }

    private static void testIntentClassification() {
        System.out.println("【1. 意图分类测试】\n");

        IntentClassifier classifier = new IntentClassifier();

        String[] tests = {
            "你好啊",
            "这菜辣不辣",
            "宫保鸡丁用什么肉",
            "红烧肉怎么做",
            "怎么申请退款",
            "我的订单到哪了",
            "门店还有宫保鸡丁吗"
        };

        for (String input : tests) {
            IntentType intent = classifier.classify(input);
            System.out.printf("  输入: %-20s → %s%n", input, intent.name());
        }
        System.out.println();
    }

    private static void testRAG() {
        System.out.println("【2. RAG问答测试】\n");

        RAGPipeline rag = new RAGPipeline();

        String[] questions = {
            "宫保鸡丁用什么肉？",
            "麻婆豆腐的做法是什么？",
            "退款规则是什么？"
        };

        for (String question : questions) {
            System.out.println("  问题: " + question);
            String answer = rag.answer(question);
            if (answer.length() > 100) {
                answer = answer.substring(0, 100) + "...";
            }
            System.out.println("  回答: " + answer.replace("\n", " "));
            System.out.println();
        }
    }

    private static void testTools() {
        System.out.println("【3. 业务工具测试】\n");

        InventoryTools inventoryTools = new InventoryTools();
        OrderTools orderTools = new OrderTools();
        RefundTools refundTools = new RefundTools();

        System.out.println("  3.1 查询门店库存:");
        System.out.println(inventoryTools.queryInventory("STORE_001", "宫保鸡丁"));

        System.out.println("\n  3.2 查询订单:");
        System.out.println(orderTools.queryOrderStatus("12345"));

        System.out.println("\n  3.3 创建退款工单:");
        System.out.println(refundTools.createRefundTicket("67890", "菜品凉了"));
    }

    private static void testFullFlow() {
        System.out.println("\n【4. 完整流程测试】\n");

        RoutingAgent routingAgent = new RoutingAgent();
        DishKnowledgeAgent dishAgent = new DishKnowledgeAgent();
        WorkOrderAgent workOrderAgent = new WorkOrderAgent();

        String[] tests = {
            "宫保鸡丁是什么菜？",
            "门店还有宫保鸡丁吗？",
            "查询订单12345的状态"
        };

        for (String input : tests) {
            System.out.println("━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━");
            System.out.println("用户输入: " + input);

            // 1. 路由决策
            RoutingDecision routing = routingAgent.route(input, null);
            System.out.println("路由决策: " + routing.targetAgent() + " | 意图: " + routing.intent().name());

            // 2. 分发处理
            AgentResponse response;
            if (routing.isDishKnowledgeRouting()) {
                response = dishAgent.answer(input, routing.context());
            } else if (routing.isWorkOrderRouting()) {
                response = workOrderAgent.process(routing.context());
            } else {
                response = AgentResponse.failure("无法处理", "Test", routing.context());
            }

            System.out.println("处理结果: " + response.getContent().substring(0, Math.min(50, response.getContent().length())) + "...");
            System.out.println();
        }
    }
}
