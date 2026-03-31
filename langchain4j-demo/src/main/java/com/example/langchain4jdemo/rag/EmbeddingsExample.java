package com.example.langchain4jdemo.rag;

import com.example.langchain4jdemo.Config;
import dev.langchain4j.data.embedding.Embedding;
import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.model.chat.ChatLanguageModel;
import dev.langchain4j.model.openai.OpenAiChatModel;
import dev.langchain4j.store.embedding.EmbeddingStore;
import dev.langchain4j.store.embedding.inmemory.InMemoryEmbeddingStore;

import java.util.*;

/**
 * 向量嵌入（Embeddings）和相似度检索示例
 * 演示 Embedding Store 的使用（概念演示）
 *
 * 注意：本示例使用模拟向量进行概念演示。
 * 实际使用时需要：
 * 1. OpenAIEmbeddingModel 或其他 EmbeddingModel 实现
 * 2. 支持的向量存储（如 Milvus、Elasticsearch 等）
 *
 * Embeddings 核心概念：
 * - EmbeddingModel: 将文本转换为向量
 * - EmbeddingStore: 存储向量和文本片段
 *
 * 应用场景：RAG（检索增强生成）的核心组件
 */
public class EmbeddingsExample {

    public static void main(String[] args) {
        Config config = Config.getInstance();

        System.out.println("=== LangChain4j 向量嵌入（Embeddings）概念示例 ===\n");

        try {
            // 创建聊天模型（用于后续生成回答）
            ChatLanguageModel chatModel = OpenAiChatModel.builder()
                    .apiKey(config.getApiKey())
                    .baseUrl(config.getBaseUrl())
                    .modelName(config.getModel())
                    .temperature(0.3)
                    .build();

            // ═══════════════════════════════════════════════════════════
            // 示例1: EmbeddingStore 概念演示
            // ═══════════════════════════════════════════════════════════
            System.out.println("╔════════════════════════════════════════════════════╗");
            System.out.println("║ 示例1: EmbeddingStore 概念演示                  ║");
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

            System.out.println("【添加文档到向量存储】\n");
            for (String doc : documents) {
                TextSegment segment = TextSegment.from(doc);
                // 实际应用中：embeddingStore.add(embeddingModel.embed(doc).content(), segment);
                embeddingStore.add(createMockEmbedding(), segment);
                System.out.println("  添加: " + doc.substring(0, Math.min(40, doc.length())) + "...");
            }

            System.out.println("\n说明：实际应用中，每个文档会通过 EmbeddingModel 转换为向量存储。");
            System.out.println("      本示例使用模拟向量进行概念演示。\n");

            // ═══════════════════════════════════════════════════════════
            // 示例2: 关键词匹配检索（模拟语义搜索）
            // ═══════════════════════════════════════════════════════════
            System.out.println("══════════════════════════════════════════════════════════\n");
            System.out.println("╔════════════════════════════════════════════════════╗");
            System.out.println("║ 示例2: 关键词匹配检索（模拟语义搜索）          ║");
            System.out.println("╚════════════════════════════════════════════════════╝\n");

            String[] queries = {
                "Java LLM 开发框架有哪些？",
                "微服务用什么框架？",
                "AI 助手能做什么？"
            };

            for (String query : queries) {
                System.out.println("查询: " + query);
                String relevantDoc = findRelevantDocument(query, documents);
                System.out.println("检索到相关文档: " + relevantDoc);
                System.out.println();
            }

            // ═══════════════════════════════════════════════════════════
            // 示例3: RAG 检索流程
            // ═══════════════════════════════════════════════════════════
            System.out.println("══════════════════════════════════════════════════════════\n");
            System.out.println("╔════════════════════════════════════════════════════╗");
            System.out.println("║ 示例3: RAG 检索流程演示                        ║");
            System.out.println("╚════════════════════════════════════════════════════╝\n");

            // 准备知识库
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
                "- Milvus - 分布式向量数据库\n" +
                "- Elasticsearch - 全文搜索引擎"
            );

            String ragQuery = "LangChain4j 有哪些核心概念？";
            System.out.println("问题: " + ragQuery + "\n");

            // Step 1: 检索（使用关键词匹配模拟）
            String retrievedContext = findRelevantDocument(ragQuery, knowledgeBase);
            System.out.println("[Step 1] 检索到相关文档:");
            System.out.println(retrievedContext.replace("\n", " "));

            // Step 2: 构建增强提示
            String augmentedPrompt = "基于以下信息回答问题。\n\n" +
                    "【相关信息】\n" + retrievedContext + "\n\n" +
                    "【问题】" + ragQuery + "\n\n" +
                    "请根据提供的信息回答。";

            // Step 3: 生成回答
            System.out.println("\n[Step 2] 生成回答...");
            String answer = chatModel.generate(augmentedPrompt);
            System.out.println("\n[Step 3] 最终回答:\n" + answer);

            System.out.println("\n══════════════════════════════════════════════════════════\n");

            // ═══════════════════════════════════════════════════════════
            // 示例4: 不同向量存储类型说明
            // ═══════════════════════════════════════════════════════════
            System.out.println("╔════════════════════════════════════════════════════╗");
            System.out.println("║ 示例4: 向量存储类型说明                        ║");
            System.out.println("╚════════════════════════════════════════════════════╝\n");

            System.out.println("LangChain4j 支持的 EmbeddingStore 实现：\n");
            System.out.println("1. InMemoryEmbeddingStore");
            System.out.println("   - 适用场景：测试、小规模数据");
            System.out.println("   - 优点：无需额外依赖");
            System.out.println("   - 缺点：数据量受内存限制\n");

            System.out.println("2. MilvusEmbeddingStore");
            System.out.println("   - 适用场景：大规模向量数据");
            System.out.println("   - 优点：分布式支持，高性能");
            System.out.println("   - 缺点：需要部署 Milvus 服务\n");

            System.out.println("3. ElasticsearchEmbeddingStore");
            System.out.println("   - 适用场景：已有 ES 集群");
            System.out.println("   - 优点：可利用现有基础设施");
            System.out.println("   - 缺点：配置复杂\n");

            System.out.println("4. PineconeEmbeddingStore");
            System.out.println("   - 适用场景：云端部署");
            System.out.println("   - 优点：托管服务，免维护");
            System.out.println("   - 缺点：需要 Pinecone 账号\n");

            System.out.println("══════════════════════════════════════════════════════════\n");
            System.out.println("示例完成！");
            System.out.println("\n【Embedding 工作流程】");
            System.out.println("1. 文档预处理 → 2. EmbeddingModel 转换为向量 → 3. 存储到 EmbeddingStore");
            System.out.println("4. 查询时同样转换为向量 → 5. 在 Store 中进行相似度搜索 → 6. 检索结果用于 RAG");

        } catch (Exception e) {
            System.err.println("错误: " + e.getMessage());
            e.printStackTrace();
        }
    }

    /**
     * 模拟创建 embedding 向量（实际应用中应使用 EmbeddingModel）
     */
    static Embedding createMockEmbedding() {
        // 模拟一个 4 维向量
        List<Float> vector = Arrays.asList(0.1f, 0.2f, 0.3f, 0.4f);
        return Embedding.from(vector);
    }

    /**
     * 简单关键词匹配（实际应用中应使用向量相似度搜索）
     */
    static String findRelevantDocument(String query, List<String> documents) {
        // 简单的关键词匹配模拟
        String[] queryWords = query.toLowerCase().split("[\\s,.，。？?！!]+");

        String bestMatch = documents.get(0);
        int maxScore = 0;

        for (String doc : documents) {
            String lowerDoc = doc.toLowerCase();
            int score = 0;
            for (String word : queryWords) {
                if (lowerDoc.contains(word)) {
                    score++;
                }
            }
            if (score > maxScore) {
                maxScore = score;
                bestMatch = doc;
            }
        }

        return bestMatch;
    }
}
