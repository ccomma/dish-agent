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

import java.util.*;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Reranking 流程单元测试
 * 测试使用向量检索 + 重排序的 RAG 流程
 * 使用 InMemoryEmbeddingStore 模拟，无需外部服务
 */
@DisplayName("Reranking 重排序流程测试")
class RerankingTest {

    private EmbeddingModel embeddingModel;
    private EmbeddingStore<TextSegment> embeddingStore;

    @BeforeEach
    void setUp() {
        DemoIntegrationTestSupport.requireEnabled();

        Config config = Config.getInstance();
        this.embeddingModel = OpenAiEmbeddingModel.builder()
                .apiKey(config.getApiKey())
                .baseUrl(config.getBaseUrl())
                .modelName("text-embedding-3-small")
                .build();
        this.embeddingStore = new InMemoryEmbeddingStore<>();
    }

    @Test
    @DisplayName("测试完整 Reranking 流程：向量检索 + 重排序")
    void testRerankingFlow() {
        // 准备知识库
        List<String> knowledgeBase = List.of(
                "LangChain4j 的核心概念包括 Models、Prompts、Memory、Chains、Agents",
                "Java 17 引入了密封类、模式匹配、记录类等新特性",
                "Spring Boot 是最流行的微服务框架，内置 Tomcat",
                "RAG 结合了信息检索和文本生成技术",
                "Cohere 提供 Embedding 和 Rerank API 服务"
        );

        // 将知识库存入向量存储
        for (String doc : knowledgeBase) {
            embeddingStore.add(embeddingModel.embed(doc).content(), TextSegment.from(doc));
        }

        String query = "Java 有哪些新特性？";

        // Step 1: 初步检索 - 召回 top-5
        Embedding queryEmbedding = embeddingModel.embed(query).content();
        EmbeddingSearchResult<TextSegment> initialResult = embeddingStore.search(
                EmbeddingSearchRequest.builder()
                        .queryEmbedding(queryEmbedding)
                        .maxResults(5)
                        .build()
        );

        assertFalse(initialResult.matches().isEmpty());
        assertTrue(initialResult.matches().size() <= 5);

        // Step 2: 模拟 Reranking（使用文本相似度重排序）
        // 由于没有 Cohere API，我们使用 Embedding 相似度作为重排序依据
        List<ScoredDocument> rerankedDocs = simulateReranking(
                initialResult.matches(), query, queryEmbedding);

        // 验证重排序结果
        assertEquals(initialResult.matches().size(), rerankedDocs.size());

        // 验证排序：第一个应该是最相关的
        String topDocText = rerankedDocs.get(0).text;
        assertTrue(topDocText.contains("Java"),
                "重排序后最相关的文档应该包含 Java: " + topDocText);

        // 验证排序正确（分数递减）
        for (int i = 1; i < rerankedDocs.size(); i++) {
            assertTrue(rerankedDocs.get(i - 1).score >= rerankedDocs.get(i).score,
                    "重排序后分数应该递减");
        }
    }

    @Test
    @DisplayName("测试多轮检索结果融合")
    void testMultipleRetrievalRounds() {
        // 准备知识库
        List<String> knowledgeBase = List.of(
                "LangChain4j 支持 OpenAI、Anthropic、Google 等多种 LLM",
                "Ollama 是本地 LLM 推理框架",
                "Milvus 是分布式向量数据库",
                "Redis 可以用作向量存储（通过模块）",
                "Elasticsearch 支持向量搜索"
        );

        for (String doc : knowledgeBase) {
            embeddingStore.add(embeddingModel.embed(doc).content(), TextSegment.from(doc));
        }

        // 第一次检索：关于 LLM 提供商
        Embedding query1 = embeddingModel.embed("LLM 提供商有哪些").content();
        EmbeddingSearchResult<TextSegment> result1 = embeddingStore.search(
                EmbeddingSearchRequest.builder()
                        .queryEmbedding(query1)
                        .maxResults(3)
                        .build()
        );

        // 第二次检索：关于向量数据库
        Embedding query2 = embeddingModel.embed("向量数据库").content();
        EmbeddingSearchResult<TextSegment> result2 = embeddingStore.search(
                EmbeddingSearchRequest.builder()
                        .queryEmbedding(query2)
                        .maxResults(3)
                        .build()
        );

        // 验证两次检索都能返回结果
        assertFalse(result1.matches().isEmpty());
        assertFalse(result2.matches().isEmpty());

        // 验证结果相关性
        boolean foundLLMRelated = result1.matches().stream()
                .anyMatch(m -> m.embedded().text().contains("LLM") ||
                        m.embedded().text().contains("OpenAI"));

        boolean foundVectorDBRelated = result2.matches().stream()
                .anyMatch(m -> m.embedded().text().contains("Milvus") ||
                        m.embedded().text().contains("向量"));

        assertTrue(foundLLMRelated, "第一次检索应该找到 LLM 相关文档");
        assertTrue(foundVectorDBRelated, "第二次检索应该找到向量数据库相关文档");
    }

    @Test
    @DisplayName("测试 top-k 选择策略")
    void testTopKSelectionStrategy() {
        // 准备知识库
        List<String> knowledgeBase = List.of(
                "文档1：关于 Java 编程",
                "文档2：关于 Python 编程",
                "文档3：关于 JavaScript",
                "文档4：关于 Go 语言",
                "文档5：关于 Rust 编程",
                "文档6：关于 C++ 编程",
                "文档7：关于 Ruby 语言"
        );

        for (String doc : knowledgeBase) {
            embeddingStore.add(embeddingModel.embed(doc).content(), TextSegment.from(doc));
        }

        String query = "编程语言";

        // 初步检索 top-7
        Embedding queryEmbedding = embeddingModel.embed(query).content();
        EmbeddingSearchResult<TextSegment> initialResult = embeddingStore.search(
                EmbeddingSearchRequest.builder()
                        .queryEmbedding(queryEmbedding)
                        .maxResults(7)
                        .build()
        );

        // 模拟重排序后选择 top-3
        List<ScoredDocument> reranked = simulateReranking(
                initialResult.matches(), query, queryEmbedding);

        List<String> top3Docs = reranked.stream()
                .limit(3)
                .map(d -> d.text)
                .collect(Collectors.toList());

        // 验证返回了3个文档
        assertEquals(3, top3Docs.size());

        // 验证所有返回的文档都包含编程相关内容
        for (String doc : top3Docs) {
            assertTrue(doc.contains("编程") || doc.contains("语言"),
                    "所有文档应该与编程/语言相关: " + doc);
        }
    }

    @Test
    @DisplayName("测试 reranking 分数计算")
    void testRerankingScoreCalculation() {
        List<String> docs = List.of(
                "Java 17 新特性介绍",
                "Python 机器学习",
                "Java 核心技术"
        );

        for (String doc : docs) {
            embeddingStore.add(embeddingModel.embed(doc).content(), TextSegment.from(doc));
        }

        String query = "Java 编程";
        Embedding queryEmbedding = embeddingModel.embed(query).content();

        EmbeddingSearchResult<TextSegment> result = embeddingStore.search(
                EmbeddingSearchRequest.builder()
                        .queryEmbedding(queryEmbedding)
                        .maxResults(3)
                        .build()
        );

        // 验证分数
        for (EmbeddingMatch<TextSegment> match : result.matches()) {
            assertTrue(match.score() >= 0.0 && match.score() <= 1.0,
                    "分数应该在 [0, 1] 范围内: " + match.score());
        }

        // Java 相关的文档应该有更高的分数
        EmbeddingMatch<TextSegment> javaDoc1 = result.matches().stream()
                .filter(m -> m.embedded().text().equals("Java 17 新特性介绍"))
                .findFirst()
                .orElse(null);

        EmbeddingMatch<TextSegment> javaDoc2 = result.matches().stream()
                .filter(m -> m.embedded().text().equals("Java 核心技术"))
                .findFirst()
                .orElse(null);

        EmbeddingMatch<TextSegment> pythonDoc = result.matches().stream()
                .filter(m -> m.embedded().text().equals("Python 机器学习"))
                .findFirst()
                .orElse(null);

        if (javaDoc1 != null && pythonDoc != null) {
            // Java 相关文档的分数应该 >= Python 文档
            assertTrue(javaDoc1.score() >= pythonDoc.score(),
                    "Java 相关文档分数应该 >= Python 文档: " + javaDoc1.score() + " >= " + pythonDoc.score());
        }
    }

    /**
     * 模拟 Reranking 流程
     * 由于没有 Cohere API，使用 Embedding 相似度作为重排序依据
     */
    private List<ScoredDocument> simulateReranking(
            List<EmbeddingMatch<TextSegment>> initialMatches,
            String query,
            Embedding queryEmbedding) {

        // 计算每个文档与查询的相似度
        List<ScoredDocument> scoredDocuments = new ArrayList<>();

        for (EmbeddingMatch<TextSegment> match : initialMatches) {
            // 使用 Embedding 相似度作为 reranking 分数
            double embeddingSimilarity = match.score();

            // 计算文本与查询的关键词重叠度（模拟 semantic reranking）
            double keywordOverlap = calculateKeywordOverlap(
                    match.embedded().text(), query);

            // 综合分数（加权平均）
            double combinedScore = embeddingSimilarity * 0.7 + keywordOverlap * 0.3;

            scoredDocuments.add(new ScoredDocument(
                    match.embedded().text(),
                    combinedScore
            ));
        }

        // 按分数降序排序
        scoredDocuments.sort((a, b) -> Double.compare(b.score, a.score));

        return scoredDocuments;
    }

    /**
     * 计算关键词重叠度
     */
    private double calculateKeywordOverlap(String text, String query) {
        Set<String> textKeywords = extractKeywords(text);
        Set<String> queryKeywords = extractKeywords(query);

        Set<String> intersection = new HashSet<>(textKeywords);
        intersection.retainAll(queryKeywords);

        Set<String> union = new HashSet<>(textKeywords);
        union.addAll(queryKeywords);

        if (union.isEmpty()) {
            return 0.0;
        }

        return (double) intersection.size() / union.size();
    }

    /**
     * 提取关键词
     */
    private Set<String> extractKeywords(String text) {
        Set<String> keywords = new HashSet<>();
        String[] words = text.toLowerCase().split("[\\s，。？?！!、，]");
        for (String word : words) {
            if (word.length() >= 2) {
                keywords.add(word);
            }
        }
        return keywords;
    }

    /**
     * 得分文档内部类
     */
    private static class ScoredDocument {
        final String text;
        final double score;

        ScoredDocument(String text, double score) {
            this.text = text;
            this.score = score;
        }
    }
}
