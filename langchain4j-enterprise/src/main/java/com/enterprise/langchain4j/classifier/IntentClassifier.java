package com.enterprise.langchain4j.classifier;

import com.enterprise.langchain4j.Config;
import dev.langchain4j.model.chat.ChatLanguageModel;
import dev.langchain4j.model.openai.OpenAiChatModel;

import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;

/**
 * 意图分类器
 * 使用关键词匹配 + LLM 辅助判断实现用户意图识别
 *
 * 设计原理：
 * - 首先使用关键词规则快速识别明确意图
 * - 对于模糊输入，使用 LLM 辅助判断
 */
public class IntentClassifier {

    // 关键词到意图的映射
    private static final Set<String> GREETING_WORDS = new HashSet<>(Arrays.asList(
        "你好", "您好", "hi", "hello", "嗨", "嘿", "早上好", "晚上好", "下午好"
    ));

    private static final Set<String> DISH_WORDS = new HashSet<>(Arrays.asList(
        "菜", "肉", "鱼", "鸡", "豆腐", "蔬菜", "口味", "辣", "咸", "甜"
    ));

    private static final Set<String> COOKING_WORDS = new HashSet<>(Arrays.asList(
        "做", "做菜", "烹饪", "炒", "煮", "蒸", "炸", "煎", "红烧", "清蒸"
    ));

    private static final Set<String> INGREDIENT_WORDS = new HashSet<>(Arrays.asList(
        "成分", "材料", "用料", "什么肉", "什么鱼", "有什么", "包含", "含有"
    ));

    private static final Set<String> POLICY_WORDS = new HashSet<>(Arrays.asList(
        "退款", "退菜", "退货", "优惠", "打折", "会员", "规则", "政策", "怎么退", "如何退"
    ));

    private static final Set<String> INVENTORY_WORDS = new HashSet<>(Arrays.asList(
        "库存", "还有", "有货", "卖完", "售罄", "有没有货", "剩"
    ));

    private static final Set<String> ORDER_WORDS = new HashSet<>(Arrays.asList(
        "订单", "到了", "状态", "送到哪", "订单号", "什么时候送", "发货"
    ));

    private static final Set<String> REFUND_WORDS = new HashSet<>(Arrays.asList(
        "申请退款", "要退款", "退款", "售后", "退单", "取消订单"
    ));

    private final ChatLanguageModel chatModel;

    public IntentClassifier() {
        Config config = Config.getInstance();

        this.chatModel = OpenAiChatModel.builder()
                .apiKey(config.getApiKey())
                .baseUrl(config.getBaseUrl())
                .modelName(config.getModel())
                .temperature(0.0)
                .build();
    }

    /**
     * 使用关键词匹配分类用户意图
     */
    public IntentType classify(String userInput) {
        String input = userInput.toLowerCase();

        // 检查是否包含各类型关键词
        if (containsAny(input, GREETING_WORDS)) {
            return IntentType.GREETING;
        }

        if (containsAny(input, REFUND_WORDS)) {
            return IntentType.CREATE_REFUND;
        }

        if (containsAny(input, ORDER_WORDS)) {
            return IntentType.QUERY_ORDER;
        }

        if (containsAny(input, INVENTORY_WORDS)) {
            return IntentType.QUERY_INVENTORY;
        }

        if (containsAny(input, POLICY_WORDS)) {
            return IntentType.POLICY_QUESTION;
        }

        if (containsAny(input, INGREDIENT_WORDS)) {
            return IntentType.DISH_INGREDIENT;
        }

        if (containsAny(input, COOKING_WORDS)) {
            return IntentType.DISH_COOKING_METHOD;
        }

        if (containsAny(input, DISH_WORDS)) {
            return IntentType.DISH_QUESTION;
        }

        // 无法通过关键词判断，询问LLM
        return classifyWithLLM(userInput);
    }

    private boolean containsAny(String input, Set<String> keywords) {
        for (String keyword : keywords) {
            if (input.contains(keyword.toLowerCase())) {
                return true;
            }
        }
        return false;
    }

    /**
     * 使用 LLM 辅助判断（用于模糊输入）
     */
    private IntentType classifyWithLLM(String userInput) {
        try {
            String prompt = "判断这个用户输入属于哪种意图：\n" +
                    "GREETING=问候, GENERAL_CHAT=闲聊, DISH_QUESTION=菜品问题\n" +
                    "POLICY_QUESTION=规则问题, QUERY_INVENTORY=库存查询, QUERY_ORDER=订单查询\n" +
                    "CREATE_REFUND=退款申请, UNKNOWN=未知\n\n" +
                    "用户输入：" + userInput + "\n\n只输出意图名称，例如 GREETING";

            String result = chatModel.generate(prompt).trim().toUpperCase();

            // 提取意图名称
            for (IntentType intent : IntentType.values()) {
                if (result.contains(intent.name())) {
                    return intent;
                }
            }

            return IntentType.UNKNOWN;
        } catch (Exception e) {
            System.err.println("LLM分类失败: " + e.getMessage());
            return IntentType.GENERAL_CHAT;
        }
    }

    /**
     * 打印分类结果
     */
    public void debugClassify(String userInput) {
        IntentType intent = classify(userInput);
        System.out.println("输入: " + userInput);
        System.out.println("识别意图: " + intent.name() + " (" + intent.getDescription() + ")");
    }

    /**
     * 批量测试意图分类
     */
    public static void main(String[] args) {
        System.out.println("=== 意图分类器测试 ===\n");

        IntentClassifier classifier = new IntentClassifier();

        String[] testInputs = {
            "你好啊",
            "今天心情不错",
            "这菜辣不辣",
            "宫保鸡丁用的是什么肉",
            "红烧肉怎么做",
            "怎么申请退款",
            "我的订单到哪了",
            "门店还有宫保鸡丁吗",
            "我要退菜",
            "你是谁"
        };

        for (String input : testInputs) {
            classifier.debugClassify(input);
            System.out.println();
        }
    }
}
