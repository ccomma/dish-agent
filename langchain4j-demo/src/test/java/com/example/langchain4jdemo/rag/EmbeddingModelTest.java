package com.example.langchain4jdemo.rag;

import com.example.langchain4jdemo.Config;
import dev.langchain4j.data.embedding.Embedding;
import dev.langchain4j.model.embedding.EmbeddingModel;
import dev.langchain4j.model.openai.OpenAiEmbeddingModel;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * EmbeddingModel 单元测试
 * 测试文本到向量的转换功能
 */
@DisplayName("EmbeddingModel 文本向量转换测试")
class EmbeddingModelTest {

    private EmbeddingModel embeddingModel;

    @BeforeEach
    void setUp() {
        Config config = Config.getInstance();
        this.embeddingModel = OpenAiEmbeddingModel.builder()
                .apiKey(config.getApiKey())
                .baseUrl(config.getBaseUrl())
                .modelName("text-embedding-3-small")
                .build();
    }

    @Test
    @DisplayName("测试单条文本嵌入转换")
    void testEmbedSingleText() {
        String text = "LangChain4j 是一个 Java LLM 框架";

        Embedding embedding = embeddingModel.embed(text).content();

        assertNotNull(embedding);
        assertNotNull(embedding.vectorAsList());
        assertFalse(embedding.vectorAsList().isEmpty());

        // text-embedding-3-small 维度为 1536
        List<Float> vector = embedding.vectorAsList();
        assertEquals(1536, vector.size());

        // 向量值应该在合理范围内
        for (Float value : vector) {
            assertTrue(value >= -1.0f && value <= 1.0f,
                    "向量值应该在 [-1, 1] 范围内");
        }
    }

    @Test
    @DisplayName("测试多条不同文本的嵌入")
    void testEmbedMultipleTexts() {
        List<String> texts = List.of(
                "LangChain4j 支持多种 LLM 提供商",
                "Java 17 引入了新特性",
                "Spring Boot 是微服务框架"
        );

        for (String text : texts) {
            Embedding embedding = embeddingModel.embed(text).content();

            assertNotNull(embedding);
            assertEquals(1536, embedding.vectorAsList().size());
        }
    }

    @Test
    @DisplayName("测试语义相似文本的向量相似度")
    void testSemanticSimilarity() {
        // 两条语义相似的文本
        String text1 = "人工智能助手可以处理问答任务";
        String text2 = "AI 助手能够回答用户问题";

        // 两条语义不同的文本
        String text3 = "今天天气很好";

        Embedding embedding1 = embeddingModel.embed(text1).content();
        Embedding embedding2 = embeddingModel.embed(text2).content();
        Embedding embedding3 = embeddingModel.embed(text3).content();

        // 计算余弦相似度
        double similarity12 = cosineSimilarity(embedding1.vectorAsList(), embedding2.vectorAsList());
        double similarity13 = cosineSimilarity(embedding1.vectorAsList(), embedding3.vectorAsList());

        // 语义相似的文本应该有更高的相似度
        assertTrue(similarity12 > similarity13,
                "语义相似的文本应该有更高的相似度: " + similarity12 + " > " + similarity13);
    }

    @Test
    @DisplayName("测试空文本处理")
    void testEmptyText() {
        String emptyText = "";

        assertThrows(Exception.class, () -> {
            embeddingModel.embed(emptyText);
        });
    }

    @Test
    @DisplayName("测试向量维度一致性")
    void testVectorDimensionConsistency() {
        // 不同长度的文本应该产生相同维度的向量
        List<String> texts = List.of(
                "短文本",
                "这是一个中等长度的文本内容",
                "这是一段非常长的文本内容，包含了更多的信息和细节，用于测试不同长度文本是否能够产生维度一致的向量嵌入表示"
        );

        Integer firstDimension = null;
        for (String text : texts) {
            Embedding embedding = embeddingModel.embed(text).content();
            int dimension = embedding.vectorAsList().size();

            if (firstDimension == null) {
                firstDimension = dimension;
            }

            assertEquals(firstDimension, dimension,
                    "所有文本的嵌入向量维度应该一致");
        }
    }

    /**
     * 计算余弦相似度
     */
    private double cosineSimilarity(List<Float> vec1, List<Float> vec2) {
        if (vec1.size() != vec2.size()) {
            throw new IllegalArgumentException("向量维度必须相同");
        }

        double dotProduct = 0.0;
        double norm1 = 0.0;
        double norm2 = 0.0;

        for (int i = 0; i < vec1.size(); i++) {
            dotProduct += vec1.get(i) * vec2.get(i);
            norm1 += vec1.get(i) * vec1.get(i);
            norm2 += vec2.get(i) * vec2.get(i);
        }

        return dotProduct / (Math.sqrt(norm1) * Math.sqrt(norm2));
    }
}