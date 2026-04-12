package com.example.langchain4jdemo.rag;

import com.example.langchain4jdemo.Config;
import dev.langchain4j.data.embedding.Embedding;
import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.model.embedding.EmbeddingModel;
import dev.langchain4j.model.openai.OpenAiChatModel;
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
 * 完整 RAG 流程测试
 * 测试检索 + 生成的完整流程
 */
@DisplayName("完整 RAG 流程测试（检索 + 生成）")
class RAGFlowTest {

    private EmbeddingModel embeddingModel;
    private ChatModel chatModel;
    private EmbeddingStore<TextSegment> embeddingStore;

    @BeforeEach
    void setUp() {
        Config config = Config.getInstance();

        this.embeddingModel = OpenAiEmbeddingModel.builder()
                .apiKey(config.getApiKey())
                .baseUrl(config.getBaseUrl())
                .modelName("text-embedding-3-small")
                .build();

        this.chatModel = OpenAiChatModel.builder()
                .apiKey(config.getApiKey())
                .baseUrl(config.getBaseUrl())
                .modelName(config.getModel())
                .temperature(0.3)
                .build();

        this.embeddingStore = new InMemoryEmbeddingStore<>();
    }

    @Test
    @DisplayName("测试完整 RAG 问答流程")
    void testCompleteRAGFlow() {
        // Step 1: 准备知识库
        List<String> knowledgeBase = List.of(
                "LangChain4j 的核心概念包括：\n" +
                        "1. Models - 与各种 LLM 交互\n" +
                        "2. Prompts - 提示模板管理\n" +
                        "3. Memory - 对话状态保持\n" +
                        "4. Chains - 多步骤处理序列\n" +
                        "5. Agents - 使用工具的 AI 实体",

                "RAG（检索增强生成）的工作流程：\n" +
                        "1. 检索（Retrieval）- 从知识库找到相关信息\n" +
                        "2. 增强（Augmentation）- 将检索结果融入提示\n" +
                        "3. 生成（Generation）- LLM 生成最终回答",

                "LangChain4j 支持的向量存储：\n" +
                        "- InMemoryEmbeddingStore - 测试用\n" +
                        "- MilvusEmbeddingStore - 分布式向量数据库\n" +
                        "- ElasticsearchEmbeddingStore - 全文搜索引擎"
        );

        // 将知识库存入向量存储
        for (String doc : knowledgeBase) {
            embeddingStore.add(embeddingModel.embed(doc).content(), TextSegment.from(doc));
        }

        // Step 2: 用户提问
        String query = "LangChain4j 有哪些核心概念？";

        // Step 3: 检索相关文档
        Embedding queryEmbedding = embeddingModel.embed(query).content();
        EmbeddingSearchResult<TextSegment> searchResult = embeddingStore.search(
                EmbeddingSearchRequest.builder()
                        .queryEmbedding(queryEmbedding)
                        .maxResults(1)
                        .build()
        );

        assertFalse(searchResult.matches().isEmpty(), "应该检索到相关文档");

        EmbeddingMatch<TextSegment> topMatch = searchResult.matches().get(0);
        String retrievedContext = topMatch.embedded().text();

        // 验证检索到的内容包含 LangChain4j
        assertTrue(retrievedContext.contains("LangChain4j") ||
                        retrievedContext.contains("Models") ||
                        retrievedContext.contains("Prompts"),
                "检索到的内容应该包含 LangChain4j 相关概念: " + retrievedContext);

        // Step 4: 构建增强提示
        String augmentedPrompt = "基于以下信息回答问题。\n\n" +
                "【相关信息】\n" + retrievedContext + "\n\n" +
                "【问题】" + query + "\n\n" +
                "请根据提供的信息回答。";

        assertNotNull(augmentedPrompt);
        assertTrue(augmentedPrompt.contains("LangChain4j 有哪些核心概念"));

        // Step 5: 调用 LLM 生成回答
        String answer = chatModel.chat(augmentedPrompt);

        assertNotNull(answer);
        assertFalse(answer.isEmpty());

        // 验证回答包含相关概念（基于检索到的内容）
        String lowerAnswer = answer.toLowerCase();
        boolean hasRelevantContent = lowerAnswer.contains("model") ||
                lowerAnswer.contains("prompt") ||
                lowerAnswer.contains("memory") ||
                lowerAnswer.contains("chain") ||
                lowerAnswer.contains("agent") ||
                lowerAnswer.contains("概念");

        assertTrue(hasRelevantContent,
                "回答应该包含与 LangChain4j 核心概念相关的内容。回答: " + answer);
    }

    @Test
    @DisplayName("测试 RAG 对特定问题的检索")
    void testRAGForSpecificQuery() {
        // 准备知识库
        List<String> knowledgeBase = List.of(
                "宫保鸡丁是一道经典川菜，主要成分是鸡胸肉、花生米、干辣椒等",
                "麻婆豆腐是四川传统名菜，主要成分是豆腐、牛肉末、豆瓣酱",
                "退款规则：上菜前可全额退款，上菜后30分钟内可换菜"
        );

        for (String doc : knowledgeBase) {
            embeddingStore.add(embeddingModel.embed(doc).content(), TextSegment.from(doc));
        }

        // 测试不同查询
        String[] queries = {
                "宫保鸡丁用什么肉？",
                "退款政策是什么？"
        };

        for (String query : queries) {
            // 检索
            Embedding queryEmbedding = embeddingModel.embed(query).content();
            EmbeddingSearchResult<TextSegment> result = embeddingStore.search(
                    EmbeddingSearchRequest.builder()
                            .queryEmbedding(queryEmbedding)
                            .maxResults(1)
                            .build()
            );

            assertFalse(result.matches().isEmpty(),
                    "对于查询 '" + query + "' 应该检索到相关内容");

            String retrieved = result.matches().get(0).embedded().text();

            // 验证检索结果与查询相关
            if (query.contains("宫保鸡丁")) {
                assertTrue(retrieved.contains("宫保鸡丁") || retrieved.contains("鸡胸肉"),
                        "检索结果应该与宫保鸡丁相关: " + retrieved);
            } else if (query.contains("退款")) {
                assertTrue(retrieved.contains("退款"),
                        "检索结果应该与退款相关: " + retrieved);
            }
        }
    }

    @Test
    @DisplayName("测试 RAG 多文档检索")
    void testRAGMultipleDocumentRetrieval() {
        // 准备知识库
        List<String> knowledgeBase = List.of(
                "Java 是一种面向对象的编程语言",
                "Python 是一种解释型的高级编程语言",
                "JavaScript 是一种脚本语言，主要用于 Web 开发",
                "Go 是 Google 开发的编译型语言"
        );

        for (String doc : knowledgeBase) {
            embeddingStore.add(embeddingModel.embed(doc).content(), TextSegment.from(doc));
        }

        // 查询编程相关内容
        String query = "编程语言有哪些？";
        Embedding queryEmbedding = embeddingModel.embed(query).content();

        EmbeddingSearchResult<TextSegment> result = embeddingStore.search(
                EmbeddingSearchRequest.builder()
                        .queryEmbedding(queryEmbedding)
                        .maxResults(4)
                        .build()
        );

        // 应该检索到多个相关文档
        assertEquals(4, result.matches().size());

        // 验证所有检索结果都与编程相关
        for (EmbeddingMatch<TextSegment> match : result.matches()) {
            String text = match.embedded().text();
            assertTrue(text.contains("Java") || text.contains("Python") ||
                            text.contains("JavaScript") || text.contains("Go") ||
                            text.contains("编程") || text.contains("语言"),
                    "检索结果应该与编程语言相关: " + text);
        }
    }

    @Test
    @DisplayName("测试 RAG 检索结果的相似度阈值")
    void testRAGSimilarityThreshold() {
        // 准备知识库
        List<String> knowledgeBase = List.of(
                "人工智能是计算机科学的一个分支",
                "今天天气很好",
                "咖啡是用烘焙的咖啡豆制成的饮料"
        );

        for (String doc : knowledgeBase) {
            embeddingStore.add(embeddingModel.embed(doc).content(), TextSegment.from(doc));
        }

        // 查询与 AI 相关的内容
        String query = "人工智能和机器学习";
        Embedding queryEmbedding = embeddingModel.embed(query).content();

        EmbeddingSearchResult<TextSegment> result = embeddingStore.search(
                EmbeddingSearchRequest.builder()
                        .queryEmbedding(queryEmbedding)
                        .maxResults(3)
                        .build()
        );

        // 验证检索结果
        assertFalse(result.matches().isEmpty());

        // 第一个结果应该与人工智能最相关
        EmbeddingMatch<TextSegment> topMatch = result.matches().get(0);
        assertTrue(topMatch.embedded().text().contains("人工智能"),
                "最相关结果应该是人工智能: " + topMatch.embedded().text());

        // 验证相似度分数
        assertTrue(topMatch.score() >= 0.0 && topMatch.score() <= 1.0);
    }

    @Test
    @DisplayName("测试 RAG 生成回答的提示构建")
    void testRAGPromptConstruction() {
        // 准备知识库
        String context = "LangChain4j 是一个 Java LLM 框架";
        embeddingStore.add(
                embeddingModel.embed(context).content(),
                TextSegment.from(context)
        );

        String query = "什么是 LangChain4j？";

        // 检索
        Embedding queryEmbedding = embeddingModel.embed(query).content();
        EmbeddingSearchResult<TextSegment> result = embeddingStore.search(
                EmbeddingSearchRequest.builder()
                        .queryEmbedding(queryEmbedding)
                        .maxResults(1)
                        .build()
        );

        String retrievedContext = result.matches().get(0).embedded().text();

        // 构建增强提示
        String prompt = buildAugmentedPrompt(retrievedContext, query);

        // 验证提示结构
        assertTrue(prompt.contains("【相关信息】"));
        assertTrue(prompt.contains("【问题】"));
        assertTrue(prompt.contains(retrievedContext));
        assertTrue(prompt.contains(query));
        assertTrue(prompt.contains("请根据提供的信息回答"));
    }

    /**
     * 构建增强提示
     */
    private String buildAugmentedPrompt(String context, String query) {
        return "基于以下信息回答问题。\n\n" +
                "【相关信息】\n" + context + "\n\n" +
                "【问题】" + query + "\n\n" +
                "请根据提供的信息回答。";
    }

    @Test
    @DisplayName("测试 RAG 流程中的向量维度一致性")
    void testEmbeddingDimensionConsistency() {
        // 准备知识库
        List<String> docs = List.of("短文本", "这是一个稍微长一点的文本内容", "这是最长的文本内容，包含了更多的信息和细节用于测试");

        for (String doc : docs) {
            embeddingStore.add(embeddingModel.embed(doc).content(), TextSegment.from(doc));
        }

        // 查询
        Embedding queryEmbedding = embeddingModel.embed("文本内容").content();

        // 验证查询向量维度
        int queryDimension = queryEmbedding.vectorAsList().size();
        assertEquals(1536, queryDimension);

        // 验证搜索结果
        EmbeddingSearchResult<TextSegment> result = embeddingStore.search(
                EmbeddingSearchRequest.builder()
                        .queryEmbedding(queryEmbedding)
                        .maxResults(3)
                        .build()
        );

        assertFalse(result.matches().isEmpty());

        // 所有检索到的文档的向量维度应该一致
        for (EmbeddingMatch<TextSegment> match : result.matches()) {
            // EmbeddingStore 内部存储的向量维度应该一致
            assertEquals(queryDimension, match.embedding().vectorAsList().size(),
                    "所有向量的维度应该一致");
        }
    }
}