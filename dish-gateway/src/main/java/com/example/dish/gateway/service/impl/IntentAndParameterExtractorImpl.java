package com.example.dish.gateway.service.impl;

import com.example.dish.common.classifier.IntentType;
import com.example.dish.gateway.dto.ExtractedData;
import com.example.dish.gateway.service.IntentAndParameterExtractor;
import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.service.AiServices;
import dev.langchain4j.service.SystemMessage;
import dev.langchain4j.service.UserMessage;
import org.springframework.stereotype.Component;

/**
 * 意图识别 + 参数抽取器。
 * 使用 LLM 一次性完成意图分类和关键业务参数抽取。
 */
@Component
public class IntentAndParameterExtractorImpl implements IntentAndParameterExtractor {

    private final ExtractionService service;

    public IntentAndParameterExtractorImpl(ChatModel routingChatModel) {
        this.service = AiServices.create(ExtractionService.class, routingChatModel);
    }

    @Override
    public ExtractedData extract(String userInput) {
        try {
            // 1. 正常路径下让结构化 AI Service 直接返回抽取结果。
            ExtractionResult result = service.extract(userInput);
            return new ExtractedData(
                result.intent(),
                result.dishName(),
                result.orderId(),
                result.refundReason(),
                false
            );
        } catch (Exception e) {
            // 2. 失败时退回 UNKNOWN，并显式标记 extractionFailed 方便上层降级。
            System.err.println("意图识别和参数抽取失败: " + e.getMessage());
            return new ExtractedData(IntentType.UNKNOWN, null, null, null, true);
        }
    }

    /**
     * 抽取结果
     */
    interface ExtractionResult {
        IntentType intent();
        String dishName();
        String orderId();
        String refundReason();
    }

    /**
     * LLM 服务接口
     */
    interface ExtractionService {
        @SystemMessage("""
            你是一个餐饮智能助手的话务分流系统。

            任务：从用户输入中识别意图并抽取关键参数。

            【意图类型】
            - GREETING: 问候语（你好、hi、hello、早安等）
            - GENERAL_CHAT: 通用闲聊（打招呼、心情、天气、闲聊等）
            - DISH_QUESTION: 菜品相关问题（口味、推荐、做法，好不好吃等）
            - DISH_INGREDIENT: 菜品成分问题（什么肉、什么材料、包含什么等）
            - DISH_COOKING_METHOD: 菜品烹饪问题（怎么做、用什么方法烹饪等）
            - POLICY_QUESTION: 餐厅政策问题（退款政策、优惠活动、会员规则等）
            - QUERY_INVENTORY: 库存查询（还有吗、有货吗、卖完没、剩多少等）
            - QUERY_ORDER: 订单查询（订单状态、到了没、订单号、什么时候送等）
            - CREATE_REFUND: 退款申请（要退款、退单、取消订单、售后等）
            - UNKNOWN: 无法判断时返回此类型

            【参数抽取规则】
            - dishName: 菜品名称，如果用户提到了具体菜品，提取出来（如"宫保鸡丁"、"麻婆豆腐"）
            - orderId: 订单号，如果用户提到了订单号或询问了具体订单，提取出来（如"12345"、"67890"）
            - refundReason: 退款原因，如果用户申请退款，提取原因（如"菜凉了"、"味道不好"等）

            【输出格式】
            只输出 JSON 格式，不要其他解释：
            {"intent":"意图类型","dishName":"菜品名称或null","orderId":"订单号或null","refundReason":"退款原因或null"}
            """)
        ExtractionResult extract(@UserMessage String userInput);
    }
}
