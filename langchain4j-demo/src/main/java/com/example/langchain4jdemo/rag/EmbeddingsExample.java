package com.example.langchain4jdemo.rag;

import com.example.langchain4jdemo.Config;
import dev.langchain4j.data.embedding.Embedding;
import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.model.embedding.EmbeddingModel;
import dev.langchain4j.model.openai.OpenAiChatModel;
import dev.langchain4j.model.openai.OpenAiEmbeddingModel;
import dev.langchain4j.model.scoring.ScoringModel;
import dev.langchain4j.model.cohere.CohereScoringModel;
import dev.langchain4j.rag.content.Content;
import dev.langchain4j.rag.query.Query;
import dev.langchain4j.store.embedding.EmbeddingSearchRequest;
import dev.langchain4j.store.embedding.EmbeddingSearchResult;
import dev.langchain4j.store.embedding.EmbeddingStore;
import dev.langchain4j.store.embedding.inmemory.InMemoryEmbeddingStore;

import java.util.*;

/**
 * 向量嵌入（Embeddings）和相似度检索完整示例
 * 演示 EmbeddingModel 和 EmbeddingStore 的完整使用流程
 *
 * 示例结构：
 * - 示例1: InMemoryEmbeddingStore 写入和相似度搜索
 * - 示例2: 完整 RAG 流程（检索+生成）
 * - 示例3: MilvusEmbeddingStore 真实向量数据库演示
 * - 示例4: 带 Reranking 的 RAG 流程
 */
public class EmbeddingsExample {

    public static void main(String[] args) {
        Config config = Config.getInstance();

        System.out.println("=== LangChain4j 向量嵌入（Embeddings）完整示例 ===\n");

        try {
            // 创建 Embedding 模型（用于文本→向量转换）
            EmbeddingModel embeddingModel = OpenAiEmbeddingModel.builder()
                    .apiKey(config.getApiKey())
                    .baseUrl(config.getBaseUrl())
                    .modelName("text-embedding-3-small")
                    .build();

            // 创建聊天模型（用于后续生成回答）
            ChatModel chatModel = OpenAiChatModel.builder()
                    .apiKey(config.getApiKey())
                    .baseUrl(config.getBaseUrl())
                    .modelName(config.getModel())
                    .temperature(0.3)
                    .build();

            // ═══════════════════════════════════════════════════════════
            // 示例1: InMemoryEmbeddingStore 写入和相似度搜索
            // ═══════════════════════════════════════════════════════════
            System.out.println("╔════════════════════════════════════════════════════╗");
            System.out.println("║ 示例1: InMemoryEmbeddingStore 写入和相似度搜索 ║");
            System.out.println("╚════════════════════════════════════════════════════╝\n");

            // 创建内存向量存储
            EmbeddingStore<TextSegment> embeddingStore = new InMemoryEmbeddingStore<>();

            // 准备文档
            List<String> documents = List.of(
                "LangChain4j 支持多种 LLM 提供商，包括 OpenAI、Anthropic、Google 等",
                "Java 17 引入了密封类、模式匹配等新特性",
                "Spring Boot 是构建微服务的主流框架",
                "人工智能助手可以处理问答、翻译、代码生成等任务",
                "Redis 是一个高性能的键值存储数据库",
                "LangChain4j 提供了 Chain、Memory、Tool 等核心组件"
            );

            System.out.println("【使用 EmbeddingModel 将文档转换为向量并存储】\n");
            for (String doc : documents) {
                // 1. 使用 EmbeddingModel 将文本转换为向量
                Embedding embedding = embeddingModel.embed(doc).content();
                // 2. 将向量和文本片段一起存储到 EmbeddingStore
                TextSegment segment = TextSegment.from(doc);
                embeddingStore.add(embedding, segment);
                System.out.println("  已存储: " + doc.substring(0, Math.min(40, doc.length())) + "...");
            }
            System.out.println("\n向量维度: " + embeddingModel.embed("test").content().vectorAsList().size());
            System.out.println("存储文档数: " + documents.size() + "\n");

            // 相似度搜索
            String[] queries = {
                "Java LLM 开发框架有哪些？",
                "微服务用什么框架？",
                "AI 助手能做什么？"
            };

            for (String query : queries) {
                System.out.println("查询: " + query);

                // 1. 将查询文本转换为向量
                Embedding queryEmbedding = embeddingModel.embed(query).content();

                // 2. 在 EmbeddingStore 中进行相似度搜索
                EmbeddingSearchRequest embeddingSearchRequest = EmbeddingSearchRequest.builder()
                        .queryEmbedding(queryEmbedding)
                        .maxResults(3)
                        .build();
                EmbeddingSearchResult<TextSegment> searchResult = embeddingStore.search(embeddingSearchRequest);

                // 3. 输出搜索结果
                System.out.println("检索到 " + searchResult.matches().size() + " 个相关文档:");
                for (int i = 0; i < searchResult.matches().size(); i++) {
                    var match = searchResult.matches().get(i);
                    System.out.println("  [" + (i + 1) + "] (相似度:" + String.format("%.3f", match.score()) + ") "
                            + match.embedded().text().substring(0, Math.min(50, match.embedded().text().length())) + "...");
                }
                System.out.println();
            }

            // ═══════════════════════════════════════════════════════════
            // 示例2: 完整 RAG 流程（检索+生成）
            // ═══════════════════════════════════════════════════════════
            System.out.println("══════════════════════════════════════════════════════════\n");
            System.out.println("╔════════════════════════════════════════════════════╗");
            System.out.println("║ 示例2: 完整RAG流程（检索+生成）               ║");
            System.out.println("╚════════════════════════════════════════════════════╝\n");

            // 准备知识库
            EmbeddingStore<TextSegment> ragStore = new InMemoryEmbeddingStore<>();

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

            // 将知识库存入 RAG EmbeddingStore
            for (String doc : knowledgeBase) {
                Embedding embedding = embeddingModel.embed(doc).content();
                ragStore.add(embedding, TextSegment.from(doc));
            }
            System.out.println("【已建立 RAG 知识库，文档数: " + knowledgeBase.size() + "】\n");

            String ragQuery = "LangChain4j 有哪些核心概念？";
            System.out.println("问题: " + ragQuery + "\n");

            // Step 1: 检索
            Embedding queryEmbedding = embeddingModel.embed(ragQuery).content();
            EmbeddingSearchResult<TextSegment> ragSearchResult = ragStore.search(
                    EmbeddingSearchRequest.builder()
                            .queryEmbedding(queryEmbedding)
                            .maxResults(1)
                            .build()
            );

            String retrievedContext = ragSearchResult.matches().get(0).embedded().text();
            System.out.println("[Step 1] 检索到最相关文档:");
            System.out.println(retrievedContext.replace("\n", " ") + "\n");

            // Step 2: 构建增强提示并生成
            String augmentedPrompt = "基于以下信息回答问题。\n\n" +
                    "【相关信息】\n" + retrievedContext + "\n\n" +
                    "【问题】" + ragQuery + "\n\n" +
                    "请根据提供的信息回答。";

            System.out.println("[Step 2] 使用 ChatModel 生成回答...");
            String answer = chatModel.chat(augmentedPrompt);
            System.out.println("\n[Step 3] 最终回答:\n" + answer);

            // ═══════════════════════════════════════════════════════════
            // 示例3: MilvusEmbeddingStore 真实向量数据库演示
            // ═══════════════════════════════════════════════════════════
            System.out.println("\n══════════════════════════════════════════════════════════\n");
            System.out.println("╔════════════════════════════════════════════════════╗");
            System.out.println("║ 示例3: MilvusEmbeddingStore 真实向量数据库演示    ║");
            System.out.println("╚════════════════════════════════════════════════════╝\n");

            // Milvus 连接配置（使用环境变量或默认地址）
            String milvusHost = System.getenv("MILVUS_HOST") != null ? System.getenv("MILVUS_HOST") : "localhost";
            String milvusPortStr = System.getenv("MILVUS_PORT");
            Integer milvusPort = milvusPortStr != null ? Integer.parseInt(milvusPortStr) : 19530;

            System.out.println("Milvus 连接配置: " + milvusHost + ":" + milvusPort);

            try {
                // 创建 MilvusEmbeddingStore
                dev.langchain4j.store.embedding.milvus.MilvusEmbeddingStore milvusStore =
                    dev.langchain4j.store.embedding.milvus.MilvusEmbeddingStore.builder()
                        .host(milvusHost)
                        .port(milvusPort)
                        .collectionName("langchain4j_demo")
                        .dimension(1536)  // text-embedding-3-small 维度
                        .build();

                // 准备文档
                List<String> milvusDocs = List.of(
                    "LangChain4j 支持多种 LLM 提供商，包括 OpenAI、Anthropic、Google 等",
                    "Java 17 引入了密封类、模式匹配等新特性",
                    "Spring Boot 是构建微服务的主流框架",
                    "人工智能助手可以处理问答、翻译、代码生成等任务"
                );

                System.out.println("\n【写入文档到 Milvus 向量数据库】");
                for (String doc : milvusDocs) {
                    Embedding embedding = embeddingModel.embed(doc).content();
                    milvusStore.add(embedding, TextSegment.from(doc));
                    System.out.println("  已存储: " + doc.substring(0, Math.min(40, doc.length())) + "...");
                }

                // 相似度搜索
                String milvusQuery = "Java LLM 开发框架有哪些？";
                System.out.println("\n查询: " + milvusQuery);

                Embedding milvusQueryEmbedding = embeddingModel.embed(milvusQuery).content();
                EmbeddingSearchResult<TextSegment> milvusResult = milvusStore.search(
                        EmbeddingSearchRequest.builder()
                                .queryEmbedding(milvusQueryEmbedding)
                                .maxResults(3)
                                .build()
                );

                System.out.println("检索到 " + milvusResult.matches().size() + " 个相关文档:");
                for (int i = 0; i < milvusResult.matches().size(); i++) {
                    var match = milvusResult.matches().get(i);
                    System.out.println("  [" + (i + 1) + "] (相似度:" + String.format("%.3f", match.score()) + ") "
                            + match.embedded().text().substring(0, Math.min(50, match.embedded().text().length())) + "...");
                }

                // 清理 collection
                milvusStore.dropCollection("langchain4j_demo");
                System.out.println("\n【Milvus collection 已清理】");

            } catch (Exception e) {
                System.out.println("\nMilvus 连接失败: " + e.getMessage());
                System.out.println("请确保 Milvus 服务已启动（docker run -d --name milvus -p 19530:19530 milvusdb/milvus:latest）");
            }

            // ═══════════════════════════════════════════════════════════
            // 示例4: 带 Reranking 的 RAG 流程
            // ═══════════════════════════════════════════════════════════
            System.out.println("\n══════════════════════════════════════════════════════════\n");
            System.out.println("╔════════════════════════════════════════════════════╗");
            System.out.println("║ 示例4: Reranking 重排序 RAG 演示                ║");
            System.out.println("╚════════════════════════════════════════════════════╝\n");

            // 准备知识库
            EmbeddingStore<TextSegment> rerankStore = new InMemoryEmbeddingStore<>();
            List<String> rerankKnowledge = List.of(
                "LangChain4j 的核心概念包括 Models、Prompts、Memory、Chains、Agents",
                "Java 17 引入了密封类、模式匹配、记录类等新特性",
                "Spring Boot 是最流行的微服务框架，内置 Tomcat",
                "RAG 结合了信息检索和文本生成技术",
                "Cohere 提供Embedding和Rerank API服务"
            );

            System.out.println("【建立知识库】");
            for (String doc : rerankKnowledge) {
                rerankStore.add(embeddingModel.embed(doc).content(), TextSegment.from(doc));
            }

            String rerankQuery = "Java 有哪些新特性？";
            System.out.println("查询: " + rerankQuery + "\n");

            // Step 1: 初步检索 - 召回 top-5
            Embedding rerankQueryEmbedding = embeddingModel.embed(rerankQuery).content();
            EmbeddingSearchResult<TextSegment> initialResult = rerankStore.search(
                    EmbeddingSearchRequest.builder()
                            .queryEmbedding(rerankQueryEmbedding)
                            .maxResults(5)
                            .build()
            );

            System.out.println("[Step 1] 初步检索结果 top-5:");
            for (int i = 0; i < initialResult.matches().size(); i++) {
                var match = initialResult.matches().get(i);
                System.out.println("  [" + (i + 1) + "] (向量相似度:" + String.format("%.3f", match.score()) + ") "
                        + match.embedded().text());
            }

            // Step 2: Reranking - 使用 Cohere ScoringModel 重排序
            String cohereApiKey = System.getenv("COHERE_API_KEY");
            if (cohereApiKey != null && !cohereApiKey.isEmpty()) {
                System.out.println("\n[Step 2] 使用 Cohere ScoringModel 进行 Reranking...");

                ScoringModel scoringModel = CohereScoringModel.builder()
                        .apiKey(cohereApiKey)
                        .build();

                // 对每个检索结果用 ScoringModel 打分
                List<TextSegment> segments = initialResult.matches().stream()
                        .map(m -> m.embedded())
                        .toList();

                // ScoringModel 对所有文本片段打分
                var scores = scoringModel.scoreAll(segments, rerankQuery);

                if (scores != null && scores.content() != null) {
                    List<Double> scoreList = scores.content();

                    // 将分数与对应文本关联
                    List<Map.Entry<Double, TextSegment>> scored = new ArrayList<>();
                    for (int i = 0; i < segments.size(); i++) {
                        scored.add(Map.entry(scoreList.get(i), segments.get(i)));
                    }

                    // 按分数降序排序
                    scored.sort((a, b) -> Double.compare(b.getKey(), a.getKey()));

                    System.out.println("\n[Step 2'] Reranking 后 top-3:");
                    for (int i = 0; i < Math.min(3, scored.size()); i++) {
                        var entry = scored.get(i);
                        System.out.println("  [" + (i + 1) + "] (Cohere分数:" + String.format("%.3f", entry.getKey()) + ") "
                                + entry.getValue().text());
                    }

                    // Step 3: 使用 Reranking 结果进行 RAG
                    String topDoc = scored.get(0).getValue().text();
                    String rerankPrompt = "基于以下信息回答问题。\n\n【相关信息】\n" + topDoc +
                            "\n\n【问题】" + rerankQuery + "\n\n请根据提供的信息回答。";

                    System.out.println("\n[Step 3] 使用 Reranking 结果生成回答...");
                    String rerankAnswer = chatModel.chat(rerankPrompt);
                    System.out.println("\n【RAG 最终回答】\n" + rerankAnswer);
                }
            } else {
                System.out.println("\n跳过 Reranking 演示（需要配置 COHERE_API_KEY）");
                System.out.println("设置环境变量: export COHERE_API_KEY=your_api_key");
            }

            System.out.println("\n══════════════════════════════════════════════════════════\n");
            System.out.println("【Embedding 工作流程总结】");
            System.out.println("1. 文档 → EmbeddingModel.embed() → Embedding 向量");
            System.out.println("2. Embedding + TextSegment → EmbeddingStore.add()");
            System.out.println("3. 查询 → EmbeddingModel.embed() → 查询向量");
            System.out.println("4. EmbeddingStore.search() → top-n 初步结果");
            System.out.println("5. ScoringModel.scoreAll() → 重新打分排序 → top-k");
            System.out.println("6. 检索结果 + 问题 → ChatModel → 最终回答");

        } catch (Exception e) {
            System.err.println("错误: " + e.getMessage());
            e.printStackTrace();
        }
    }
}
