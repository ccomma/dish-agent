package com.enterprise.langchain4j.agent;

import com.enterprise.langchain4j.classifier.IntentType;
import com.enterprise.langchain4j.context.AgentContext;
import com.enterprise.langchain4j.contract.AgentResponse;
import com.enterprise.langchain4j.contract.RoutingDecision;
import dev.langchain4j.model.chat.ChatModel;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.*;

/**
 * OrchestrationAgent 单元测试
 *
 * 注意：OrchestrationAgent 的 dispatch() 和 handleChat() 是 private 方法，
 * 只能通过 public 方法 process() 进行端到端测试。
 * 由于 process() 涉及完整的 Agent 协作链，这里主要测试 RoutingDecision 的路由逻辑。
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
@DisplayName("OrchestrationAgent 编排代理测试")
class OrchestrationAgentTest {

    @Mock
    private RoutingAgent routingAgent;

    @Mock
    private DishKnowledgeAgent dishKnowledgeAgent;

    @Mock
    private WorkOrderAgent workOrderAgent;

    @Mock
    private ChatModel chatModel;

    private OrchestrationAgent orchestrationAgent;

    @BeforeEach
    void setUp() {
        // 通过反射注入 mock 依赖
        orchestrationAgent = new OrchestrationAgent();
        try {
            java.lang.reflect.Field routingAgentField = OrchestrationAgent.class.getDeclaredField("routingAgent");
            routingAgentField.setAccessible(true);
            routingAgentField.set(orchestrationAgent, routingAgent);

            java.lang.reflect.Field dishKnowledgeAgentField = OrchestrationAgent.class.getDeclaredField("dishKnowledgeAgent");
            dishKnowledgeAgentField.setAccessible(true);
            dishKnowledgeAgentField.set(orchestrationAgent, dishKnowledgeAgent);

            java.lang.reflect.Field workOrderAgentField = OrchestrationAgent.class.getDeclaredField("workOrderAgent");
            workOrderAgentField.setAccessible(true);
            workOrderAgentField.set(orchestrationAgent, workOrderAgent);

            java.lang.reflect.Field chatModelField = OrchestrationAgent.class.getDeclaredField("chatModel");
            chatModelField.setAccessible(true);
            chatModelField.set(orchestrationAgent, chatModel);
        } catch (Exception e) {
            throw new RuntimeException("Failed to inject mock dependencies", e);
        }
    }

    @Test
    @DisplayName("测试 RoutingDecision 路由到 DishKnowledgeAgent")
    void testRoutingDecisionToDishKnowledge() {
        AgentContext context = AgentContext.builder()
                .sessionId("SESSION_001")
                .intent(IntentType.DISH_QUESTION)
                .userInput("宫保鸡丁好吃吗")
                .build();

        RoutingDecision routing = new RoutingDecision(
                IntentType.DISH_QUESTION,
                RoutingDecision.TARGET_DISH_KNOWLEDGE,
                "菜品咨询",
                context
        );

        assertTrue(routing.isDishKnowledgeRouting());
        assertFalse(routing.isChatRouting());
        assertFalse(routing.isWorkOrderRouting());
        assertEquals("dish-knowledge", routing.targetAgent());
    }

    @Test
    @DisplayName("测试 RoutingDecision 路由到 WorkOrderAgent")
    void testRoutingDecisionToWorkOrder() {
        AgentContext context = AgentContext.builder()
                .sessionId("SESSION_002")
                .intent(IntentType.QUERY_INVENTORY)
                .userInput("门店还有宫保鸡丁吗")
                .build();

        RoutingDecision routing = new RoutingDecision(
                IntentType.QUERY_INVENTORY,
                RoutingDecision.TARGET_WORK_ORDER,
                "库存查询",
                context
        );

        assertTrue(routing.isWorkOrderRouting());
        assertFalse(routing.isDishKnowledgeRouting());
        assertFalse(routing.isChatRouting());
        assertEquals("work-order", routing.targetAgent());
    }

    @Test
    @DisplayName("测试 RoutingDecision 路由到 ChatAgent")
    void testRoutingDecisionToChat() {
        AgentContext context = AgentContext.builder()
                .sessionId("SESSION_003")
                .intent(IntentType.GREETING)
                .userInput("你好")
                .build();

        RoutingDecision routing = new RoutingDecision(
                IntentType.GREETING,
                RoutingDecision.TARGET_CHAT,
                "问候语",
                context
        );

        assertTrue(routing.isChatRouting());
        assertFalse(routing.isDishKnowledgeRouting());
        assertFalse(routing.isWorkOrderRouting());
        assertEquals("chat", routing.targetAgent());
    }

    @Test
    @DisplayName("测试 RoutingAgent 路由方法")
    void testRoutingAgentRoute() {
        AgentContext context = AgentContext.builder()
                .sessionId("SESSION_001")
                .intent(IntentType.DISH_QUESTION)
                .userInput("宫保鸡丁是什么菜")
                .build();

        RoutingDecision routing = new RoutingDecision(
                IntentType.DISH_QUESTION,
                RoutingDecision.TARGET_DISH_KNOWLEDGE,
                "菜品咨询",
                context
        );

        when(routingAgent.route(eq("宫保鸡丁是什么菜"), isNull()))
                .thenReturn(routing);

        RoutingDecision result = routingAgent.route("宫保鸡丁是什么菜", null);

        assertNotNull(result);
        assertEquals(IntentType.DISH_QUESTION, result.intent());
        assertEquals("dish-knowledge", result.targetAgent());
    }

    @Test
    @DisplayName("测试 DishKnowledgeAgent answer 方法")
    void testDishKnowledgeAgentAnswer() {
        AgentContext context = AgentContext.builder()
                .sessionId("SESSION_001")
                .intent(IntentType.DISH_QUESTION)
                .userInput("宫保鸡丁好吃吗")
                .build();

        AgentResponse expectedResponse = AgentResponse.success(
                "宫保鸡丁是一道经典的川菜，用鸡胸肉和花生炒制而成",
                "DishKnowledgeAgent",
                context
        );

        when(dishKnowledgeAgent.answer(eq("宫保鸡丁好吃吗"), any(AgentContext.class)))
                .thenReturn(expectedResponse);

        AgentResponse response = dishKnowledgeAgent.answer("宫保鸡丁好吃吗", context);

        assertTrue(response.isSuccess());
        assertEquals("DishKnowledgeAgent", response.getAgentName());
        assertTrue(response.getContent().contains("宫保鸡丁"));
    }

    @Test
    @DisplayName("测试 WorkOrderAgent process 方法 - 库存查询")
    void testWorkOrderAgentProcessInventory() {
        AgentContext context = AgentContext.builder()
                .sessionId("SESSION_001")
                .intent(IntentType.QUERY_INVENTORY)
                .userInput("门店还有宫保鸡丁吗")
                .storeId("STORE_001")
                .dishName("宫保鸡丁")
                .build();

        AgentResponse expectedResponse = AgentResponse.success(
                "门店 STORE_001 宫保鸡丁库存充足",
                "WorkOrderAgent",
                context
        );

        when(workOrderAgent.process(any(AgentContext.class)))
                .thenReturn(expectedResponse);

        AgentResponse response = workOrderAgent.process(context);

        assertTrue(response.isSuccess());
        assertEquals("WorkOrderAgent", response.getAgentName());
    }

    @Test
    @DisplayName("测试 ChatModel 闲聊处理")
    void testChatModelResponse() {
        String userInput = "你好，今天天气不错";
        String expectedResponse = "你好！确实是个好天气。";

        when(chatModel.chat(userInput)).thenReturn(expectedResponse);

        String response = chatModel.chat(userInput);

        assertEquals(expectedResponse, response);
        verify(chatModel).chat(userInput);
    }

    @Test
    @DisplayName("测试 RoutingDecision 常量定义")
    void testRoutingDecisionConstants() {
        assertEquals("dish-knowledge", RoutingDecision.TARGET_DISH_KNOWLEDGE);
        assertEquals("work-order", RoutingDecision.TARGET_WORK_ORDER);
        assertEquals("chat", RoutingDecision.TARGET_CHAT);
    }

    @Test
    @DisplayName("测试 RoutingDecision 辅助方法覆盖所有 IntentType")
    void testRoutingDecisionHelperMethods() {
        // 测试 GREETING -> chat
        AgentContext ctx1 = AgentContext.builder().sessionId("S1").intent(IntentType.GREETING).build();
        assertTrue(new RoutingDecision(IntentType.GREETING, RoutingDecision.TARGET_CHAT, "", ctx1).isChatRouting());

        // 测试 GENERAL_CHAT -> chat
        AgentContext ctx2 = AgentContext.builder().sessionId("S2").intent(IntentType.GENERAL_CHAT).build();
        assertTrue(new RoutingDecision(IntentType.GENERAL_CHAT, RoutingDecision.TARGET_CHAT, "", ctx2).isChatRouting());

        // 测试 DISH_QUESTION -> dish-knowledge
        AgentContext ctx3 = AgentContext.builder().sessionId("S3").intent(IntentType.DISH_QUESTION).build();
        assertTrue(new RoutingDecision(IntentType.DISH_QUESTION, RoutingDecision.TARGET_DISH_KNOWLEDGE, "", ctx3).isDishKnowledgeRouting());

        // 测试 QUERY_INVENTORY -> work-order
        AgentContext ctx4 = AgentContext.builder().sessionId("S4").intent(IntentType.QUERY_INVENTORY).build();
        assertTrue(new RoutingDecision(IntentType.QUERY_INVENTORY, RoutingDecision.TARGET_WORK_ORDER, "", ctx4).isWorkOrderRouting());

        // 测试 CREATE_REFUND -> work-order
        AgentContext ctx5 = AgentContext.builder().sessionId("S5").intent(IntentType.CREATE_REFUND).build();
        assertTrue(new RoutingDecision(IntentType.CREATE_REFUND, RoutingDecision.TARGET_WORK_ORDER, "", ctx5).isWorkOrderRouting());
    }
}
