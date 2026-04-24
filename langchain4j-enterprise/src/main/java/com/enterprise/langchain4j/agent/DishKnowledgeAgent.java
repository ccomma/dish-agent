package com.enterprise.langchain4j.agent;

import com.enterprise.langchain4j.Config;
import com.enterprise.langchain4j.classifier.IntentType;
import com.enterprise.langchain4j.context.AgentContext;
import com.enterprise.langchain4j.contract.AgentResponse;
import com.enterprise.langchain4j.rag.RAGPipeline;
import dev.langchain4j.memory.chat.MessageWindowChatMemory;
import dev.langchain4j.model.chat.ChatModel;
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

        ChatModel chatModel = OpenAiChatModel.builder()
                .apiKey(config.getApiKey())
                .baseUrl(config.getBaseUrl())
                .modelName(config.getModel())
                .temperature(0.3)
                .build();

        this.ragPipeline = new RAGPipeline();

        System.out.println("[DishKnowledgeAgent] RAG pipeline initialized ("
                + "vector store: " + config.getVectorStoreType() + ")");

        this.assistant = AiServices.builder(DishKnowledgeAssistant.class)
                .chatModel(chatModel)
                .chatMemory(MessageWindowChatMemory.withMaxMessages(10))
                .build();
    }

    public DishKnowledgeAgent(RAGPipeline ragPipeline, ChatModel chatModel) {
        this.ragPipeline = ragPipeline;

        this.assistant = AiServices.builder(DishKnowledgeAssistant.class)
                .chatModel(chatModel)
                .chatMemory(MessageWindowChatMemory.withMaxMessages(10))
                .build();
    }

    /**
     * 处理菜品知识问答
     */
    public AgentResponse answer(String userQuestion, AgentContext context) {
        try {
            // 1. 通过 RAG 管道完成检索和回答生成。
            String answer = ragPipeline.answer(userQuestion);

            // 2. 再补充 follow-up hints，构造统一 AgentResponse。
            List<String> hints = generateFollowUpHints(context.getIntent());

            return AgentResponse.builder()
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

    /**
     * 带自反思的 ReAct 版本回答
     *
     * 流程：
     * 1. 首次 RAG 检索
     * 2. 自反思：评估检索结果是否充分
     * 3. 如果不充分，补充检索
     * 4. 整合结果生成最终回答
     */
    public AgentResponse answerWithReflection(String userQuestion, AgentContext context, ReActState state) {
        try {
            // 1. 首次 RAG 检索。
            String initialAnswer = ragPipeline.answer(userQuestion);
            state.addStep(ReActState.StepType.OBSERVATION,
                    "首次RAG检索完成", "DishKnowledgeAgent");

            // 2. 自反思：评估结果是否充分。
            boolean isSufficient = reflectOnResult(initialAnswer, userQuestion);

            String finalAnswer;
            if (isSufficient) {
                state.addStep(ReActState.StepType.THOUGHT,
                        "检索结果充分，直接使用", "DishKnowledgeAgent");
                finalAnswer = initialAnswer;
            } else {
                state.addStep(ReActState.StepType.THOUGHT,
                        "检索结果不充分，进行补充检索", "DishKnowledgeAgent");

                // 3. 补充检索：扩写 query 再检索一次。
                String expandedQuery = expandQuery(userQuestion);
                String expandedAnswer = ragPipeline.answer(expandedQuery);
                state.addStep(ReActState.StepType.OBSERVATION,
                        "补充检索完成", "DishKnowledgeAgent");

                // 4. 整合两次检索结果。
                finalAnswer = integrateResults(initialAnswer, expandedAnswer);
            }

            List<String> hints = generateFollowUpHints(context.getIntent());

            return AgentResponse.builder()
                    .success(true)
                    .content(finalAnswer)
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

    /**
     * 自反思：评估检索结果是否充分
     *
     * 检查点：
     * 1. 回答是否为空或太短
     * 2. 是否包含"不知道"、"无法回答"等模糊表述
     * 3. 是否覆盖了用户问题的关键实体
     */
    private boolean reflectOnResult(String answer, String question) {
        // 太短认为不充分
        if (answer == null || answer.length() < 20) {
            return false;
        }

        // 包含模糊表述认为不充分
        String[] vaguePhrases = {"不知道", "无法回答", "没有找到", "不清楚"};
        for (String phrase : vaguePhrases) {
            if (answer.contains(phrase)) {
                return false;
            }
        }

        // 检查是否提取到了关键实体（简单检查）
        // 如果问题中有具体菜品名，但回答中没有提到，可能不充分
        // 这里简化处理，实际可以用 NER 或其他方法

        return true;
    }

    /**
     * 扩展查询以获取更多信息
     */
    private String expandQuery(String originalQuery) {
        // 简单策略：如果原查询是"X是什么"，补充"X的相关信息"
        if (originalQuery.contains("什么")) {
            return originalQuery + "，包括做法、配料、口味等";
        }
        if (originalQuery.contains("怎么做") || originalQuery.contains("如何做")) {
            return originalQuery + "，包括步骤、技巧、注意事项";
        }
        // 默认追加营养和热量信息
        return originalQuery + "，包括营养成分、热量等";
    }

    /**
     * 整合多个检索结果
     */
    private String integrateResults(String initial, String expanded) {
        // 如果初始结果已经很好，就用初始的
        // 否则可以结合两者，但这里简化处理
        if (initial.contains("不知道") || initial.contains("无法")) {
            return expanded;
        }
        return initial;
    }

    private List<String> generateFollowUpHints(IntentType intent) {
        if (intent == null) {
            return Collections.emptyList();
        }

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
