package com.enterprise.langchain4j.rag;

import dev.langchain4j.data.embedding.Embedding;
import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.model.embedding.EmbeddingModel;
import dev.langchain4j.model.scoring.ScoringModel;
import dev.langchain4j.store.embedding.EmbeddingMatch;
import dev.langchain4j.store.embedding.EmbeddingSearchResult;
import dev.langchain4j.store.embedding.EmbeddingStore;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.stubbing.Answer;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * RAGPipeline 单元测试
 */
@ExtendWith(MockitoExtension.class)
class RAGPipelineTest {

    @Mock
    private ChatModel mockChatModel;

    @Mock
    private EmbeddingService mockEmbeddingService;

    @Mock
    private EmbeddingStore<TextSegment> mockEmbeddingStore;

    @Mock
    private ScoringModel mockScoringModel;

    @Mock
    private EmbeddingModel mockEmbeddingModel;

    @Mock
    private Embedding mockEmbedding;

    @Mock
    private EmbeddingSearchResult<TextSegment> mockSearchResult;

    private RAGPipeline ragPipeline;

    @BeforeEach
    void setUp() throws Exception {
        // Setup mock embedding service
        when(mockEmbeddingService.embed(anyString())).thenReturn(mockEmbedding);

        // Setup mock search result
        TextSegment segment1 = TextSegment.from("【宫保鸡丁】\n宫保鸡丁是一道经典的川菜。");
        EmbeddingMatch<TextSegment> match1 = new EmbeddingMatch<>(0.95, "id1", mockEmbedding, segment1);
        when(mockSearchResult.matches()).thenReturn(List.of(match1));
        when(mockEmbeddingStore.search(any())).thenReturn(mockSearchResult);

        // Create RAGPipeline with mocked dependencies
        ragPipeline = new RAGPipeline(
                mockEmbeddingService,
                mockEmbeddingStore,
                null,  // no scoring model for unit tests
                mockChatModel,
                false  // reranking disabled
        );

        // Set the knowledgeBase field for knowledge base tests
        Field knowledgeBaseField = RAGPipeline.class.getDeclaredField("knowledgeBase");
        knowledgeBaseField.setAccessible(true);
        Map<?, ?> kb = (Map<?, ?>) knowledgeBaseField.get(ragPipeline);
        if (kb == null) {
            java.lang.reflect.Field modifiersField = Field.class.getDeclaredField("modifiers");
            modifiersField.setAccessible(true);
            modifiersField.setInt(knowledgeBaseField, knowledgeBaseField.getModifiers() & ~java.lang.reflect.Modifier.FINAL);
            knowledgeBaseField.set(ragPipeline, new java.util.LinkedHashMap<>());
        }
    }

    @Test
    void testEmbeddingServiceIsUsed() {
        // Verify embedding service is called
        verify(mockEmbeddingService, never()).embed(anyString()); // Not called yet

        // Trigger a retrieval which should call embed
        Method retrieveMethod;
        try {
            retrieveMethod = RAGPipeline.class.getDeclaredMethod("retrieve", String.class, int.class);
            retrieveMethod.setAccessible(true);
            retrieveMethod.invoke(ragPipeline, "宫保鸡丁", 3);
        } catch (Exception e) {
            // Expected since knowledge base is empty
        }

        verify(mockEmbeddingService, atLeastOnce()).embed(anyString());
    }

    @Test
    void testEmbeddingStoreIsSearched() {
        // Trigger a retrieval
        try {
            Method retrieveMethod = RAGPipeline.class.getDeclaredMethod("retrieve", String.class, int.class);
            retrieveMethod.setAccessible(true);
            retrieveMethod.invoke(ragPipeline, "宫保鸡丁", 3);
        } catch (Exception e) {
            // Expected since knowledge base is empty
        }

        // Verify embedding store was searched
        verify(mockEmbeddingStore, atLeastOnce()).search(any());
    }

    @Test
    void testAnswer_ChatModelIsCalled() {
        // Setup mock chat model
        when(mockChatModel.chat(anyString()))
                .thenAnswer((Answer<String>) invocation -> {
                    String prompt = invocation.getArgument(0);
                    return "测试回答";
                });

        String answer = ragPipeline.answer("宫保鸡丁的价格是多少？");

        assertNotNull(answer);
        verify(mockChatModel, atLeastOnce()).chat(anyString());
    }

    @Test
    void testAnswer_ReturnsMockResponse() {
        when(mockChatModel.chat(anyString()))
                .thenReturn("宫保鸡丁使用鸡胸肉作为主料");

        String answer = ragPipeline.answer("宫保鸡丁用什么肉？");

        assertNotNull(answer);
        assertEquals("宫保鸡丁使用鸡胸肉作为主料", answer);
    }

    @Test
    void testRetrieve_ReturnsContextFromEmbeddingStore() throws Exception {
        Method retrieveMethod = RAGPipeline.class.getDeclaredMethod("retrieve", String.class, int.class);
        retrieveMethod.setAccessible(true);

        String context = (String) retrieveMethod.invoke(ragPipeline, "宫保鸡丁", 3);

        assertNotNull(context);
        verify(mockEmbeddingStore, atLeastOnce()).search(any());
    }
}
