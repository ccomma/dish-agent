package com.example.langchain4jdemo.rag;

import com.example.langchain4jdemo.Config;
import dev.langchain4j.data.embedding.Embedding;
import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.model.embedding.EmbeddingModel;
import dev.langchain4j.model.openai.OpenAiChatModel;
import dev.langchain4j.model.openai.OpenAiEmbeddingModel;
import dev.langchain4j.store.embedding.EmbeddingSearchResult;
import dev.langchain4j.store.embedding.EmbeddingStore;
import dev.langchain4j.store.embedding.inmemory.InMemoryEmbeddingStore;

import java.util.*;

/**
 * 向量嵌入（Embeddings）和相似度检索示例
 * 演示 EmbeddingModel 和 EmbeddingStore 的完整使用流程
 *
 * 核心概念：
 * - EmbeddingModel: 将文本转换为向量（Embedding）
 * - EmbeddingStore: 存储向量和对应的文本片段，支持相似度搜索
 *
 * 应用场景：RAG（检索增强生成）的核心组件
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
            // 示例1: 使用 EmbeddingModel 将文档写入 EmbeddingStore
            // ═══════════════════════════════════════════════════════════
            System.out.println("╔════════════════════════════════════════════════════╗");
            System.out.println("║ 示例1: 文档Embedding写入EmbeddingStore          ║");
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

            // ═══════════════════════════════════════════════════════════
            // 示例2: 使用 EmbeddingStore 进行相似度搜索
            // ═══════════════════════════════════════════════════════════
            System.out.println("══════════════════════════════════════════════════════════\n");
            System.out.println("╔════════════════════════════════════════════════════╗");
            System.out.println("║ 示例2: EmbeddingStore相似度搜索                 ║");
            System.out.println("╚════════════════════════════════════════════════════╝\n");

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
                //    搜索返回与查询向量最相似的 top-3 结果
                EmbeddingSearchResult<TextSegment> searchResult = embeddingStore.search(
                        dev.langchain4j.store.embedding.EmbeddingSearchRequest.builder()
                                .queryEmbedding(queryEmbedding)
                                .maxResults(3)
                                .build()
                );

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
            // 示例3: 完整 RAG 流程（EmbeddingStore + ChatModel）
            // ═══════════════════════════════════════════════════════════
            System.out.println("══════════════════════════════════════════════════════════\n");
            System.out.println("╔════════════════════════════════════════════════════╗");
            System.out.println("║ 示例3: 完整RAG流程（检索+生成）               ║");
            System.out.println("╚════════════════════════════════════════════════════╝\n");

            // 准备知识库（使用 EmbeddingModel 存入新的 EmbeddingStore）
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

            // Step 1: 检索（使用 EmbeddingStore 相似度搜索）
            Embedding queryEmbedding = embeddingModel.embed(ragQuery).content();
            EmbeddingSearchResult<TextSegment> ragSearchResult = ragStore.search(
                    dev.langchain4j.store.embedding.EmbeddingSearchRequest.builder()
                            .queryEmbedding(queryEmbedding)
                            .maxResults(1)
                            .build()
            );

            String retrievedContext = ragSearchResult.matches().get(0).embedded().text();
            System.out.println("[Step 1] 检索到最相关文档:");
            System.out.println(retrievedContext.replace("\n", " ") + "\n");

            // Step 2: 构建增强提示
            String augmentedPrompt = "基于以下信息回答问题。\n\n" +
                    "【相关信息】\n" + retrievedContext + "\n\n" +
                    "【问题】" + ragQuery + "\n\n" +
                    "请根据提供的信息回答。";

            // Step 3: 生成回答
            System.out.println("[Step 2] 使用 ChatModel 生成回答...");
            String answer = chatModel.chat(augmentedPrompt);
            System.out.println("\n[Step 3] 最终回答:\n" + answer);

            System.out.println("\n══════════════════════════════════════════════════════════\n");

            // ═══════════════════════════════════════════════════════════
            // 示例4: MilvusEmbeddingStore 使用说明
            // ═══════════════════════════════════════════════════════════
            System.out.println("╔════════════════════════════════════════════════════╗");
            System.out.println("║ 示例4: MilvusEmbeddingStore 配置说明              ║");
            System.out.println("╚════════════════════════════════════════════════════╝\n");

            System.out.println("MilvusEmbeddingStore 使用示例（需要 Milvus 服务）:\n");
            System.out.println("```java");
            System.out.println("// Milvus 连接配置");
            System.out.println("MilvusEmbeddingStore store = MilvusEmbeddingStore.builder()");
            System.out.println("    .host(\"localhost\")");
            System.out.println("    .port(\"19530\")");
            System.out.println("    .collectionName(\"langchain4j_demo\")");
            System.out.println("    .embeddingModel(embeddingModel)  // 传入 EmbeddingModel");
            System.out.println("    .build();");
            System.out.println("```\n");

            System.out.println("【配置参数说明】");
            System.out.println("- host/port: Milvus 服务地址");
            System.out.println("- collectionName: 向量集合名称");
            System.out.println("- embeddingModel: 自动将文本转换为向量\n");

            System.out.println("【Docker 启动 Milvus 参考】");
            System.out.println("```bash");
            System.out.println("docker run -d --name milvus \\");
            System.out.println("  -p 19530:19530 \\");
            System.out.println("  -p 9091:9091 \\");
            System.out.println("  milvusdb/milvus:latest");
            System.out.println("```\n");

            System.out.println("══════════════════════════════════════════════════════════\n");
            System.out.println("【Embedding 工作流程】");
            System.out.println("1. 文档 → EmbeddingModel.embed() → Embedding 向量");
            System.out.println("2. Embedding + TextSegment → EmbeddingStore.add()");
            System.out.println("3. 查询 → EmbeddingModel.embed() → 查询向量");
            System.out.println("4. EmbeddingStore.search() → 相似度排序结果");
            System.out.println("5. 检索结果 + 问题 → ChatModel → 最终回答");

        } catch (Exception e) {
            System.err.println("错误: " + e.getMessage());
            e.printStackTrace();
        }
    }
}
