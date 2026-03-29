package com.enterprise.langchain4j;

/**
 * 餐饮Agent自动测试
 * 用于验证Agent各个模块的功能
 */
public class DishConsultingAgentTest {

    public static void main(String[] args) {
        System.out.println("╔══════════════════════════════════════════╗");
        System.out.println("║   餐饮智能助手 - 企业级模块测试        ║");
        System.out.println("╚══════════════════════════════════════════╝\n");

        // 1. 测试意图分类
        testIntentClassification();

        // 2. 测试RAG问答
        testRAG();

        // 3. 测试SaaS工具
        testSaaSClient();

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
            // 只打印前100字
            if (answer.length() > 100) {
                answer = answer.substring(0, 100) + "...";
            }
            System.out.println("  回答: " + answer.replace("\n", " "));
            System.out.println();
        }
    }

    private static void testSaaSClient() {
        System.out.println("【3. SaaS工具测试】\n");

        SaaSClient client = new SaaSClient();

        System.out.println("  3.1 查询门店库存:");
        System.out.println(client.queryInventory("STORE_001", "宫保鸡丁"));

        System.out.println("\n  3.2 查询订单:");
        System.out.println(client.queryOrderStatus("12345"));

        System.out.println("\n  3.3 创建退款工单:");
        System.out.println(client.createRefundTicket("67890", "菜品凉了"));
    }
}
