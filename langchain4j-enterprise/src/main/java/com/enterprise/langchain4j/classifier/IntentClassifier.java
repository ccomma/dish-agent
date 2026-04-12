package com.enterprise.langchain4j.classifier;

import com.enterprise.langchain4j.Config;
import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.model.openai.OpenAiChatModel;
import dev.langchain4j.service.AiServices;
import dev.langchain4j.service.SystemMessage;
import dev.langchain4j.service.UserMessage;

/**
 * 意图分类器
 * 使用 AiServices 结构化输出实现 LLM-based 意图识别
 *
 * 设计原理：
 * - 使用 AiServices 将 LLM 响应直接映射到 IntentType 枚举
 * - 支持零样本分类，泛化能力强
 */
public class IntentClassifier {

    // LLM 分类服务接口 - 使用结构化输出
    interface IntentClassificationService {

        @SystemMessage("""
            你是一个餐饮智能助手的话务分流系统。
            根据用户输入，判断其意图属于以下哪种类型：

            GREETING - 问候语（你好、hi、hello、早安等）
            GENERAL_CHAT - 通用闲聊（打招呼、心情、天气、闲聊等）
            DISH_QUESTION - 菜品相关问题（口味、推荐、做法，好不好吃等）
            DISH_INGREDIENT - 菜品成分问题（什么肉、什么材料、包含什么等）
            DISH_COOKING_METHOD - 菜品烹饪问题（怎么做、用什么方法烹饪等）
            POLICY_QUESTION - 餐厅政策问题（退款政策、优惠活动、会员规则等）
            QUERY_INVENTORY - 库存查询（还有吗、有货吗、卖完没、剩多少等）
            QUERY_ORDER - 订单查询（订单状态、到了没、订单号、什么时候送等）
            CREATE_REFUND - 退款申请（要退款、退单、取消订单、售后等）
            UNKNOWN - 无法判断时返回此类型

            只输出意图类型名称，不要解释。
            """)
        IntentType classify(@UserMessage String userInput);
    }

    private final IntentClassificationService classificationService;

    public IntentClassifier() {
        Config config = Config.getInstance();

        ChatModel chatModel = OpenAiChatModel.builder()
                .apiKey(config.getApiKey())
                .baseUrl(config.getBaseUrl())
                .modelName(config.getModel())
                .temperature(0.0)  // 确定性输出
                .build();

        // 使用 AiServices 创建结构化分类服务
        this.classificationService = AiServices.create(IntentClassificationService.class, chatModel);
    }

    /**
     * 使用 LLM 分类用户意图
     */
    public IntentType classify(String userInput) {
        try {
            return classificationService.classify(userInput);
        } catch (Exception e) {
            System.err.println("意图分类失败: " + e.getMessage());
            return IntentType.GENERAL_CHAT;
        }
    }

}
