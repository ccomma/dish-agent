package com.example.langchain4jdemo.rag;

import com.example.langchain4jdemo.Config;
import dev.langchain4j.data.embedding.Embedding;
import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.model.embedding.EmbeddingModel;
import dev.langchain4j.model.openai.OpenAiEmbeddingModel;
import dev.langchain4j.store.embedding.EmbeddingMatch;
import dev.langchain4j.store.embedding.EmbeddingSearchRequest;
import dev.langchain4j.store.embedding.EmbeddingSearchResult;
import dev.langchain4j.store.embedding.EmbeddingStore;
import dev.langchain4j.store.embedding.inmemory.InMemoryEmbeddingStore;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * EmbeddingStore 单元测试
 * 测试向量的添加和搜索操作
 */
@DisplayName("EmbeddingStore 添加和搜索测试")
class EmbeddingStoreTest {

    private EmbeddingModel embeddingModel;
    private EmbeddingStore<TextSegment> embeddingStore;

    @BeforeEach
    void setUp() {
        Config config = Config.getInstance();
        this.embeddingModel = OpenAiEmbeddingModel.builder()
                .apiKey(config.getApiKey())
                .baseUrl(config.getBaseUrl())
                .modelName("text-embedding-3-small")
                .build();
        this.embeddingStore = new InMemoryEmbeddingStore<>();
    }

    @Test
    @DisplayName("测试添加单个向量")
    void testAddSingleEmbedding() {
        String text = "LangChain4j 是一个 Java LLM 框架";
        Embedding embedding = embeddingModel.embed(text).content();
        TextSegment segment = TextSegment.from(text);

        String id = embeddingStore.add(embedding, segment);

        assertNotNull(id);
        assertFalse(id.isEmpty());
    }

    @Test
    @DisplayName("测试添加多个向量")
    void testAddMultipleEmbeddings() {
        List<String> documents = List.of(
                "LangChain4j 支持多种 LLM 提供商",
                "Java 17 引入了新特性",
                "Spring Boot 是微服务框架",
                "Redis 是高性能键值存储"
        );

        for (String doc : documents) {
            Embedding embedding = embeddingModel.embed(doc).content();
            embeddingStore.add(embedding, TextSegment.from(doc));
        }

        // 验证添加成功 - 通过搜索验证
        Embedding queryEmbedding = embeddingModel.embed("Java 框架").content();
        EmbeddingSearchResult<TextSegment> result = embeddingStore.search(
                EmbeddingSearchRequest.builder()
                        .queryEmbedding(queryEmbedding)
                        .maxResults(10)
                        .build()
        );

        assertEquals(4, result.matches().size());
    }

    @Test
    @DisplayName("测试相似度搜索 - 语义相关内容")
    void testSimilaritySearch() {
        // 添加文档
        List<String> documents = List.of(
                "LangChain4j 的核心概念包括 Models、Chains、Memory",
                "Java 17 引入了密封类和记录类",
                "Spring Boot 内置 Tomcat 服务器",
                "人工智能助手可以回答问题"
        );

        for (String doc : documents) {
            embeddingStore.add(embeddingModel.embed(doc).content(), TextSegment.from(doc));
        }

        // 查询与 Java/LLM 相关的文档
        Embedding queryEmbedding = embeddingModel.embed("Java LLM 开发").content();
        EmbeddingSearchResult<TextSegment> result = embeddingStore.search(
                EmbeddingSearchRequest.builder()
                        .queryEmbedding(queryEmbedding)
                        .maxResults(2)
                        .build()
        );

        assertFalse(result.matches().isEmpty());
        assertTrue(result.matches().size() <= 2);

        // 最相关的结果应该是关于 LangChain4j 的
        EmbeddingMatch<TextSegment> topMatch = result.matches().get(0);
        assertTrue(topMatch.embedded().text().contains("LangChain4j") ||
                        topMatch.embedded().text().contains("Java"),
                "最相关结果应该包含 Java 或 LangChain4j: " + topMatch.embedded().text());
    }

    @Test
    @DisplayName("测试搜索结果按相似度排序")
    void testSearchResultsOrderedBySimilarity() {
        // 添加语义差异明显的文档
        List<String> documents = List.of(
                "水果包括苹果、香蕉、橙子等",
                "编程语言有 Java、Python、JavaScript",
                "动物有猫、狗、大象等"
        );

        for (String doc : documents) {
            embeddingStore.add(embeddingModel.embed(doc).content(), TextSegment.from(doc));
        }

        // 查询编程相关内容
        Embedding queryEmbedding = embeddingModel.embed("软件开发").content();
        EmbeddingSearchResult<TextSegment> result = embeddingStore.search(
                EmbeddingSearchRequest.builder()
                        .queryEmbedding(queryEmbedding)
                        .maxResults(3)
                        .build()
        );

        List<EmbeddingMatch<TextSegment>> matches = result.matches();

        // 验证结果按相似度降序排列
        for (int i = 1; i < matches.size(); i++) {
            assertTrue(matches.get(i - 1).score() >= matches.get(i).score(),
                    "结果应该按相似度降序排列");
        }

        // 编程语言相关的文档应该排在最前面
        assertTrue(matches.get(0).embedded().text().contains("Java") ||
                        matches.get(0).embedded().text().contains("Python") ||
                        matches.get(0).embedded().text().contains("编程"),
                "最相关结果应该是编程相关内容: " + matches.get(0).embedded().text());
    }

    @Test
    @DisplayName("测试 maxResults 参数限制")
    void testMaxResultsLimit() {
        // 添加多个文档
        List<String> documents = List.of(
                "文档1",
                "文档2",
                "文档3",
                "文档4",
                "文档5"
        );

        for (String doc : documents) {
            embeddingStore.add(embeddingModel.embed(doc).content(), TextSegment.from(doc));
        }

        // 请求最多2个结果
        Embedding queryEmbedding = embeddingModel.embed("文档").content();
        EmbeddingSearchResult<TextSegment> result = embeddingStore.search(
                EmbeddingSearchRequest.builder()
                        .queryEmbedding(queryEmbedding)
                        .maxResults(2)
                        .build()
        );

        assertEquals(2, result.matches().size());
    }

    @Test
    @DisplayName("测试空存储的搜索")
    void testSearchEmptyStore() {
        Embedding queryEmbedding = embeddingModel.embed("任何查询").content();
        EmbeddingSearchResult<TextSegment> result = embeddingStore.search(
                EmbeddingSearchRequest.builder()
                        .queryEmbedding(queryEmbedding)
                        .maxResults(5)
                        .build()
        );

        assertTrue(result.matches().isEmpty());
    }

    @Test
    @DisplayName("测试相似度分数范围")
    void testSimilarityScoreRange() {
        // 添加一个确定相关的文档
        String doc = "人工智能是计算机科学的一个分支";
        embeddingStore.add(embeddingModel.embed(doc).content(), TextSegment.from(doc));

        // 查询完全相同的内容
        Embedding queryEmbedding = embeddingModel.embed(doc).content();
        EmbeddingSearchResult<TextSegment> result = embeddingStore.search(
                EmbeddingSearchRequest.builder()
                        .queryEmbedding(queryEmbedding)
                        .maxResults(1)
                        .build()
        );

        assertFalse(result.matches().isEmpty());

        double score = result.matches().get(0).score();
        // 相似度分数应该在 [0, 1] 范围内
        assertTrue(score >= 0.0 && score <= 1.0,
                "相似度分数应该在 [0, 1] 范围内: " + score);

        // 相同内容的相似度应该非常高（接近1）
        assertTrue(score > 0.9,
                "相同或极相似内容的相似度应该 > 0.9: " + score);
    }

    @Test
    @DisplayName("测试检索结果包含原始文本")
    void testRetrievalContainsOriginalText() {
        String originalText = "LangChain4j 是一个强大的 Java LLM 框架";
        embeddingStore.add(
                embeddingModel.embed(originalText).content(),
                TextSegment.from(originalText)
        );

        Embedding queryEmbedding = embeddingModel.embed("Java LLM 框架").content();
        EmbeddingSearchResult<TextSegment> result = embeddingStore.search(
                EmbeddingSearchRequest.builder()
                        .queryEmbedding(queryEmbedding)
                        .maxResults(1)
                        .build()
        );

        assertFalse(result.matches().isEmpty());
        assertEquals(originalText, result.matches().get(0).embedded().text());
    }
}