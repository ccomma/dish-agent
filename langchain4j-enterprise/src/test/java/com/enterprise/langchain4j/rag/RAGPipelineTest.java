package com.enterprise.langchain4j.rag;

import dev.langchain4j.model.chat.ChatModel;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.stubbing.Answer;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

/**
 * RAGPipeline 单元测试
 */
@ExtendWith(MockitoExtension.class)
class RAGPipelineTest {

    @Mock
    private ChatModel mockChatModel;

    private RAGPipeline ragPipeline;

    @BeforeEach
    void setUp() throws Exception {
        // Create a real RAGPipeline instance
        ragPipeline = new RAGPipeline();

        // Inject mock ChatModel using reflection
        Field chatModelField = RAGPipeline.class.getDeclaredField("chatModel");
        chatModelField.setAccessible(true);
        chatModelField.set(ragPipeline, mockChatModel);
    }

    @Test
    void testKnowledgeBaseIsLoaded() throws Exception {
        // Access the private knowledgeBase field
        Field knowledgeBaseField = RAGPipeline.class.getDeclaredField("knowledgeBase");
        knowledgeBaseField.setAccessible(true);

        @SuppressWarnings("unchecked")
        Map<String, ?> knowledgeBase = (Map<String, ?>) knowledgeBaseField.get(ragPipeline);

        assertNotNull(knowledgeBase);
        assertFalse(knowledgeBase.isEmpty(), "Knowledge base should not be empty");
    }

    @Test
    void testLoadDishes_DishEntriesExist() throws Exception {
        Field knowledgeBaseField = RAGPipeline.class.getDeclaredField("knowledgeBase");
        knowledgeBaseField.setAccessible(true);

        @SuppressWarnings("unchecked")
        Map<String, ?> knowledgeBase = (Map<String, ?>) knowledgeBaseField.get(ragPipeline);

        assertTrue(knowledgeBase.containsKey("宫保鸡丁"), "宫保鸡丁 should be loaded");
        assertTrue(knowledgeBase.containsKey("麻婆豆腐"), "麻婆豆腐 should be loaded");
    }

    @Test
    void testLoadPolicies_PolicyEntriesExist() throws Exception {
        Field knowledgeBaseField = RAGPipeline.class.getDeclaredField("knowledgeBase");
        knowledgeBaseField.setAccessible(true);

        @SuppressWarnings("unchecked")
        Map<String, ?> knowledgeBase = (Map<String, ?>) knowledgeBaseField.get(ragPipeline);

        assertTrue(knowledgeBase.containsKey("退款规则"), "退款规则 should be loaded");
    }

    @Test
    void testRetrieve_ReturnsContextWithReferences() throws Exception {
        // Use reflection to call private retrieve method
        Method retrieveMethod = RAGPipeline.class.getDeclaredMethod("retrieve", String.class, int.class);
        retrieveMethod.setAccessible(true);

        String context = (String) retrieveMethod.invoke(ragPipeline, "宫保鸡丁", 3);

        assertNotNull(context);
        assertTrue(context.contains("参考信息") || context.contains("宫保鸡丁"),
                "Context should contain reference information");
    }

    @Test
    void testRetrieve_WithPolicyQuery() throws Exception {
        Method retrieveMethod = RAGPipeline.class.getDeclaredMethod("retrieve", String.class, int.class);
        retrieveMethod.setAccessible(true);

        String context = (String) retrieveMethod.invoke(ragPipeline, "退款政策", 3);

        assertNotNull(context);
    }

    @Test
    void testAnswer_ReturnsResponse() {
        // Mock the chat model response
        when(mockChatModel.chat(anyString()))
                .thenReturn("宫保鸡丁使用鸡胸肉作为主料");

        String answer = ragPipeline.answer("宫保鸡丁用什么肉？");

        assertNotNull(answer);
        assertEquals("宫保鸡丁使用鸡胸肉作为主料", answer);
        verify(mockChatModel, atLeastOnce()).chat(anyString());
    }

    @Test
    void testAnswer_WithPolicyQuestion() {
        // Mock the chat model response
        when(mockChatModel.chat(anyString()))
                .thenReturn("退款将在3-7个工作日内原路返回");

        String answer = ragPipeline.answer("退款要多久到账？");

        assertNotNull(answer);
        assertEquals("退款将在3-7个工作日内原路返回", answer);
        verify(mockChatModel, atLeastOnce()).chat(anyString());
    }

    @Test
    void testAnswer_DishQuestion() {
        when(mockChatModel.chat(anyString()))
                .thenReturn("麻婆豆腐的辣度是3级（中辣）");

        String answer = ragPipeline.answer("麻婆豆腐辣不辣？");

        assertNotNull(answer);
        assertEquals("麻婆豆腐的辣度是3级（中辣）", answer);
        verify(mockChatModel, atLeastOnce()).chat(anyString());
    }

    @Test
    void testAnswer_ChatModelIsCalled() {
        // Setup mock to capture the prompt
        when(mockChatModel.chat(anyString()))
                .thenAnswer((Answer<String>) invocation -> {
                    String prompt = invocation.getArgument(0);
                    assertTrue(prompt.contains("宫保鸡丁"));
                    assertTrue(prompt.contains("参考信息"));
                    return "测试回答";
                });

        String answer = ragPipeline.answer("宫保鸡丁的价格是多少？");

        assertNotNull(answer);
        assertEquals("测试回答", answer);
        verify(mockChatModel, times(1)).chat(anyString());
    }

    @Test
    void testRetrieve_WithNoMatches() throws Exception {
        Method retrieveMethod = RAGPipeline.class.getDeclaredMethod("retrieve", String.class, int.class);
        retrieveMethod.setAccessible(true);

        String context = (String) retrieveMethod.invoke(ragPipeline, "完全不匹配的查询xyz123", 3);

        assertNotNull(context);
        assertTrue(context.contains("未找到直接相关的参考信息") || context.contains("参考信息"));
    }
}
