package com.enterprise.langchain4j.agent;

import com.enterprise.langchain4j.Config;
import com.enterprise.langchain4j.classifier.IntentType;
import com.enterprise.langchain4j.context.AgentContext;
import com.enterprise.langchain4j.contract.AgentResponse;
import com.enterprise.langchain4j.rag.RAGPipeline;
import dev.langchain4j.memory.chat.MessageWindowChatMemory;
import dev.langchain4j.model.chat.ChatLanguageModel;
import dev.langchain4j.model.openai.OpenAiChatModel;
import dev.langchain4j.service.AiServices;
import dev.langchain4j.service.SystemMessage;

import java.util.Collections;
import java.util.List;

/**
 * 菜品知识Agent
 *
 * 职责边界：
 * 1. 接收来自RoutingAgent的已路由请求
 * 2. 调用RAGPipeline检索相关知识
 * 3. 结合上下文生成专业回答
 * 4. 返回AgentResponse给编排层
 */
public class DishKnowledgeAgent {

    /**
     * 菜品知识助手接口
     * 使用LangChain4j AiServices构建
     */
    public interface DishKnowledgeAssistant {
        @SystemMessage("""
            你是一个专业的餐饮知识助手，负责回答用户关于菜品的问题。

            你的职责：
            1. 回答菜品的成分、材料问题
            2. 提供菜品的做法和烹饪技巧
            3. 介绍菜品的风味和营养信息
            4. 解释门店的政策和规则

            重要原则：
            - 基于提供的参考信息回答，不要编造
            - 如果参考信息不足，明确告知用户
            - 回答要专业、清晰、易懂
            """)
        String answerDishQuestion(String question);
    }

    private final RAGPipeline ragPipeline;
    private final DishKnowledgeAssistant assistant;

    public DishKnowledgeAgent() {
        Config config = Config.getInstance();

        ChatLanguageModel chatModel = OpenAiChatModel.builder()
                .apiKey(config.getApiKey())
                .baseUrl(config.getBaseUrl())
                .modelName(config.getModel())
                .temperature(0.3)
                .build();

        this.ragPipeline = new RAGPipeline();

        this.assistant = AiServices.builder(DishKnowledgeAssistant.class)
                .chatLanguageModel(chatModel)
                .chatMemory(MessageWindowChatMemory.withMaxMessages(10))
                .build();
    }

    public DishKnowledgeAgent(RAGPipeline ragPipeline, ChatLanguageModel chatModel) {
        this.ragPipeline = ragPipeline;

        this.assistant = AiServices.builder(DishKnowledgeAssistant.class)
                .chatLanguageModel(chatModel)
                .chatMemory(MessageWindowChatMemory.withMaxMessages(10))
                .build();
    }

    /**
     * 处理菜品知识问答
     */
    public AgentResponse answer(String userQuestion, AgentContext context) {
        try {
            // 1. 使用RAG管道检索+生成
            String answer = ragPipeline.answer(userQuestion);

            // 2. 构建响应（附带后续操作提示）
            List<String> hints = generateFollowUpHints(context.getIntent());

            return AgentResponse.success(answer, "DishKnowledgeAgent", context)
                    .getFollowUpHints().isEmpty()
                    ? AgentResponse.builder()
                        .success(true)
                        .content(answer)
                        .agentName("DishKnowledgeAgent")
                        .context(context)
                        .followUpHints(hints)
                        .build()
                    : AgentResponse.builder()
                        .success(true)
                        .content(answer)
                        .agentName("DishKnowledgeAgent")
                        .context(context)
                        .followUpHints(hints)
                        .build();

        } catch (Exception e) {
            return AgentResponse.failure(
                    "抱歉，查询菜品信息时出现问题: " + e.getMessage(),
                    "DishKnowledgeAgent",
                    context
            );
        }
    }

    private List<String> generateFollowUpHints(IntentType intent) {
        if (intent == null) return Collections.emptyList();

        return switch (intent) {
            case DISH_QUESTION -> List.of(
                    "您可能还想知道这道菜的做法",
                    "我可以帮您查询门店库存"
            );
            case DISH_INGREDIENT -> List.of(
                    "想了解这道菜的口味吗",
                    "我可以告诉您库存情况"
            );
            case DISH_COOKING_METHOD -> List.of(
                    "想了解这道菜的营养信息吗",
                    "我可以帮您查询门店库存"
            );
            case POLICY_QUESTION -> List.of(
                    "需要帮您申请退款吗",
                    "我可以查询您的订单状态"
            );
            default -> Collections.emptyList();
        };
    }
}
