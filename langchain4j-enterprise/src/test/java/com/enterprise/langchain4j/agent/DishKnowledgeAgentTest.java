package com.enterprise.langchain4j.agent;

import com.enterprise.langchain4j.classifier.IntentType;
import com.enterprise.langchain4j.context.AgentContext;
import com.enterprise.langchain4j.contract.AgentResponse;
import com.enterprise.langchain4j.rag.RAGPipeline;
import dev.langchain4j.model.chat.ChatModel;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/**
 * DishKnowledgeAgent 单元测试
 *
 * 测试 DishKnowledgeAgent 的问答逻辑和后续提示生成
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("DishKnowledgeAgent 菜品知识代理测试")
class DishKnowledgeAgentTest {

    @Mock
    private RAGPipeline ragPipeline;

    @Mock
    private ChatModel chatModel;

    private DishKnowledgeAgent dishKnowledgeAgent;

    @BeforeEach
    void setUp() {
        // 使用接受 RAGPipeline 和 ChatModel 的构造函数
        dishKnowledgeAgent = new DishKnowledgeAgent(ragPipeline, chatModel);
    }

    @Test
    @DisplayName("测试 answer() 返回成功的 AgentResponse")
    void testAnswerReturnsSuccessResponse() {
        String userQuestion = "宫保鸡丁用什么肉？";
        AgentContext context = AgentContext.builder()
                .sessionId("SESSION_001")
                .intent(IntentType.DISH_INGREDIENT)
                .dishName("宫保鸡丁")
                .userInput(userQuestion)
                .build();

        when(ragPipeline.answer(userQuestion))
                .thenReturn("宫保鸡丁主要使用鸡胸肉或鸡腿肉，切成丁状后烹饪。");

        AgentResponse response = dishKnowledgeAgent.answer(userQuestion, context);

        assertTrue(response.isSuccess());
        assertEquals("DishKnowledgeAgent", response.getAgentName());
        assertEquals("宫保鸡丁主要使用鸡胸肉或鸡腿肉，切成丁状后烹饪。", response.getContent());
        assertNotNull(response.getContext());
        verify(ragPipeline).answer(userQuestion);
    }

    @Test
    @DisplayName("测试 answer() 调用 RAGPipeline.answer()")
    void testAnswerCallsRAGPipeline() {
        String userQuestion = "宫保鸡丁怎么做？";
        AgentContext context = AgentContext.builder()
                .sessionId("SESSION_002")
                .intent(IntentType.DISH_COOKING_METHOD)
                .userInput(userQuestion)
                .build();

        when(ragPipeline.answer(userQuestion))
                .thenReturn("宫保鸡丁的做法：...\n1. 鸡丁腌制\n2. 调制宫保汁...");

        dishKnowledgeAgent.answer(userQuestion, context);

        verify(ragPipeline, times(1)).answer(userQuestion);
    }

    @Test
    @DisplayName("测试 answer() 处理 RAGPipeline 异常返回失败响应")
    void testAnswerHandlesException() {
        String userQuestion = "某道菜的问题";
        AgentContext context = AgentContext.builder()
                .sessionId("SESSION_003")
                .intent(IntentType.DISH_QUESTION)
                .userInput(userQuestion)
                .build();

        when(ragPipeline.answer(userQuestion))
                .thenThrow(new RuntimeException("RAG pipeline error"));

        AgentResponse response = dishKnowledgeAgent.answer(userQuestion, context);

        assertFalse(response.isSuccess());
        assertEquals("DishKnowledgeAgent", response.getAgentName());
        assertTrue(response.getContent().contains("抱歉"));
        assertTrue(response.getContent().contains("RAG pipeline error"));
    }

    @Test
    @DisplayName("测试 answer() 为 DISH_QUESTION 生成后续提示")
    void testAnswerGeneratesFollowUpHintsForDishQuestion() {
        String userQuestion = "这道菜好吃吗";
        AgentContext context = AgentContext.builder()
                .sessionId("SESSION_004")
                .intent(IntentType.DISH_QUESTION)
                .dishName("宫保鸡丁")
                .userInput(userQuestion)
                .build();

        when(ragPipeline.answer(userQuestion))
                .thenReturn("宫保鸡丁是一道很受欢迎的川菜，口味鲜香麻辣。");

        AgentResponse response = dishKnowledgeAgent.answer(userQuestion, context);

        assertNotNull(response.getFollowUpHints());
        assertFalse(response.getFollowUpHints().isEmpty());
        // DISH_QUESTION 应该有2个后续提示
        assertEquals(2, response.getFollowUpHints().size());
        assertTrue(response.getFollowUpHints().stream()
                .anyMatch(h -> h.contains("做法")));
        assertTrue(response.getFollowUpHints().stream()
                .anyMatch(h -> h.contains("库存")));
    }

    @Test
    @DisplayName("测试 answer() 为 DISH_INGREDIENT 生成后续提示")
    void testAnswerGeneratesFollowUpHintsForDishIngredient() {
        String userQuestion = "宫保鸡丁的成分是什么";
        AgentContext context = AgentContext.builder()
                .sessionId("SESSION_005")
                .intent(IntentType.DISH_INGREDIENT)
                .dishName("宫保鸡丁")
                .userInput(userQuestion)
                .build();

        when(ragPipeline.answer(userQuestion))
                .thenReturn("宫保鸡丁的主要成分是鸡胸肉、花生米、干辣椒等。");

        AgentResponse response = dishKnowledgeAgent.answer(userQuestion, context);

        assertNotNull(response.getFollowUpHints());
        assertEquals(2, response.getFollowUpHints().size());
        assertTrue(response.getFollowUpHints().stream()
                .anyMatch(h -> h.contains("口味")));
        assertTrue(response.getFollowUpHints().stream()
                .anyMatch(h -> h.contains("库存")));
    }

    @Test
    @DisplayName("测试 answer() 为 DISH_COOKING_METHOD 生成后续提示")
    void testAnswerGeneratesFollowUpHintsForDishCookingMethod() {
        String userQuestion = "宫保鸡丁怎么做";
        AgentContext context = AgentContext.builder()
                .sessionId("SESSION_006")
                .intent(IntentType.DISH_COOKING_METHOD)
                .dishName("宫保鸡丁")
                .userInput(userQuestion)
                .build();

        when(ragPipeline.answer(userQuestion))
                .thenReturn("宫保鸡丁的做法步骤：...\n1. 鸡丁腌制\n2. 炸花生米...");

        AgentResponse response = dishKnowledgeAgent.answer(userQuestion, context);

        assertNotNull(response.getFollowUpHints());
        assertEquals(2, response.getFollowUpHints().size());
        assertTrue(response.getFollowUpHints().stream()
                .anyMatch(h -> h.contains("营养")));
        assertTrue(response.getFollowUpHints().stream()
                .anyMatch(h -> h.contains("库存")));
    }

    @Test
    @DisplayName("测试 answer() 为 POLICY_QUESTION 生成后续提示")
    void testAnswerGeneratesFollowUpHintsForPolicyQuestion() {
        String userQuestion = "退款政策是什么";
        AgentContext context = AgentContext.builder()
                .sessionId("SESSION_007")
                .intent(IntentType.POLICY_QUESTION)
                .userInput(userQuestion)
                .build();

        when(ragPipeline.answer(userQuestion))
                .thenReturn("退款政策：\n1. 上桌前可全额退款\n2. 上桌后30分钟内可换菜...");

        AgentResponse response = dishKnowledgeAgent.answer(userQuestion, context);

        assertNotNull(response.getFollowUpHints());
        assertEquals(2, response.getFollowUpHints().size());
        assertTrue(response.getFollowUpHints().stream()
                .anyMatch(h -> h.contains("退款")));
        assertTrue(response.getFollowUpHints().stream()
                .anyMatch(h -> h.contains("订单")));
    }

    @Test
    @DisplayName("测试 answer() 为未知意图返回空的后续提示")
    void testAnswerWithNullIntentReturnsEmptyHints() {
        String userQuestion = "一些问题";
        AgentContext context = AgentContext.builder()
                .sessionId("SESSION_008")
                .intent(null)
                .userInput(userQuestion)
                .build();

        when(ragPipeline.answer(userQuestion))
                .thenReturn("抱歉，我无法回答这个问题。");

        AgentResponse response = dishKnowledgeAgent.answer(userQuestion, context);

        assertTrue(response.getFollowUpHints() == null || response.getFollowUpHints().isEmpty());
    }

    @Test
    @DisplayName("测试不同意图类型生成不同的后续提示")
    void testDifferentIntentsGenerateDifferentFollowUpHints() {
        // DISH_QUESTION
        AgentContext ctx1 = AgentContext.builder()
                .sessionId("SESSION_009")
                .intent(IntentType.DISH_QUESTION)
                .build();
        when(ragPipeline.answer("问题1")).thenReturn("回答1");
        List<String> hints1 = dishKnowledgeAgent.answer("问题1", ctx1).getFollowUpHints();
        assertNotNull(hints1);
        assertFalse(hints1.isEmpty());

        // DISH_INGREDIENT
        AgentContext ctx2 = AgentContext.builder()
                .sessionId("SESSION_010")
                .intent(IntentType.DISH_INGREDIENT)
                .build();
        when(ragPipeline.answer("问题2")).thenReturn("回答2");
        List<String> hints2 = dishKnowledgeAgent.answer("问题2", ctx2).getFollowUpHints();
        assertNotNull(hints2);
        assertFalse(hints2.isEmpty());

        // DISH_COOKING_METHOD
        AgentContext ctx3 = AgentContext.builder()
                .sessionId("SESSION_011")
                .intent(IntentType.DISH_COOKING_METHOD)
                .build();
        when(ragPipeline.answer("问题3")).thenReturn("回答3");
        List<String> hints3 = dishKnowledgeAgent.answer("问题3", ctx3).getFollowUpHints();
        assertNotNull(hints3);
        assertFalse(hints3.isEmpty());

        // POLICY_QUESTION
        AgentContext ctx4 = AgentContext.builder()
                .sessionId("SESSION_012")
                .intent(IntentType.POLICY_QUESTION)
                .build();
        when(ragPipeline.answer("问题4")).thenReturn("回答4");
        List<String> hints4 = dishKnowledgeAgent.answer("问题4", ctx4).getFollowUpHints();
        assertNotNull(hints4);
        assertFalse(hints4.isEmpty());

        // 不同意图类型的提示应该不完全相同
        assertNotEquals(hints1, hints2);
        assertNotEquals(hints2, hints3);
        assertNotEquals(hints3, hints4);
    }

    @Test
    @DisplayName("测试 answer() 保留上下文中所有参数")
    void testAnswerPreservesContextParams() {
        String userQuestion = "宫保鸡丁的问题";
        AgentContext context = AgentContext.builder()
                .sessionId("SESSION_013")
                .intent(IntentType.DISH_QUESTION)
                .storeId("STORE_001")
                .dishName("宫保鸡丁")
                .userInput(userQuestion)
                .build();

        when(ragPipeline.answer(userQuestion))
                .thenReturn("回答内容");

        AgentResponse response = dishKnowledgeAgent.answer(userQuestion, context);

        assertEquals(context, response.getContext());
        assertEquals("STORE_001", response.getContext().getStoreId());
        assertEquals("宫保鸡丁", response.getContext().getDishName());
    }

    @Test
    @DisplayName("测试 answer() 响应包含 agentName")
    void testAnswerResponseContainsAgentName() {
        String userQuestion = "菜品问题";
        AgentContext context = AgentContext.builder()
                .sessionId("SESSION_014")
                .intent(IntentType.DISH_QUESTION)
                .userInput(userQuestion)
                .build();

        when(ragPipeline.answer(userQuestion))
                .thenReturn("回答内容");

        AgentResponse response = dishKnowledgeAgent.answer(userQuestion, context);

        assertEquals("DishKnowledgeAgent", response.getAgentName());
    }
}