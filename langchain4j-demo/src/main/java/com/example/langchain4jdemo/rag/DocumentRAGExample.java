package com.example.langchain4jdemo.rag;

import com.example.langchain4jdemo.Config;
import dev.langchain4j.data.document.Document;
import dev.langchain4j.data.document.parser.TextDocumentParser;
import dev.langchain4j.memory.chat.MessageWindowChatMemory;
import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.model.openai.OpenAiChatModel;
import dev.langchain4j.service.AiServices;
import dev.langchain4j.service.SystemMessage;
import dev.langchain4j.service.UserMessage;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

/**
 * 文档加载和检索增强生成（RAG）示例
 * 演示如何加载文档并进行问答
 */
public class DocumentRAGExample {

    // RAG 助手接口 - 使用 @SystemMessage 和 @UserMessage 组合
    interface RAGAssistant {
        @SystemMessage("你是一个有帮助的AI助手，基于提供的文档内容回答用户的问题。如果文档中没有相关信息，请说明你不知道。")
        String answer(@UserMessage String question);
    }

    // 模拟的 RAG 处理器 - 实际应用中会用向量数据库
    static String performRAG(String question, String documentContent, ChatModel model) {
        // 简单关键词匹配 - 实际应用中应使用向量相似度检索
        String relevantContext = documentContent;

        // 根据问题选择相关上下文（简化版）
        if (question.contains("核心概念") || question.contains("概念")) {
            relevantContext = extractSection(documentContent, "核心概念");
        } else if (question.contains("RAG")) {
            relevantContext = extractSection(documentContent, "RAG");
        } else if (question.contains("LangChain4j") || question.contains("什么")) {
            relevantContext = extractSection(documentContent, "LangChain4j");
        }

        String prompt = "基于以下文档内容回答问题。\n\n【文档内容】\n" + relevantContext +
                        "\n\n【问题】" + question +
                        "\n\n请根据文档内容回答，如果文档没有相关信息请说明。";

        return model.chat(prompt);
    }

    static String extractSection(String content, String keyword) {
        // 简化：返回包含关键词的段落
        String[] lines = content.split("\n");
        StringBuilder relevant = new StringBuilder();
        for (String line : lines) {
            if (line.contains(keyword) || !relevant.isEmpty()) {
                relevant.append(line).append("\n");
                if (relevant.length() > 500) break; // 限制长度
            }
        }
        return !relevant.isEmpty() ? relevant.toString() : content;
    }

    public static void main(String[] args) {
        // 从配置文件加载配置
        Config config = Config.getInstance();

        System.out.println("=== 文档加载和RAG示例 ===");

        try {
            // 1. 创建聊天模型
            System.out.println("\n1. 创建Minimax聊天模型...");
            ChatModel chatModel = OpenAiChatModel.builder()
                    .apiKey(config.getApiKey())
                    .baseUrl(config.getBaseUrl())
                    .modelName(config.getModel())
                    .temperature(0.3)
                    .build();

            // 2. 创建示例文档内容
            System.out.println("2. 创建示例文档内容...");

            String documentText = """
                LangChain4j 是一个 Java 框架，用于构建基于大型语言模型（LLM）的应用程序。
                它提供了与各种 LLM 提供商的集成，包括 OpenAI、Azure OpenAI、Google Vertex AI、Hugging Face 等。

                LangChain4j 的核心概念包括：
                1. 模型（Models）：与 LLM 交互的接口
                2. 提示模板（Prompt Templates）：可重复使用的提示结构
                3. 链（Chains）：组合多个组件的序列
                4. 记忆（Memory）：在对话中保持状态
                5. 代理（Agents）：使用工具自主行动的 AI
                6. 检索器（Retrievers）：从外部知识源检索信息

                RAG（检索增强生成）是一种技术，它结合了信息检索和文本生成。
                在 RAG 中，系统首先从知识库中检索相关文档，然后将这些文档作为上下文提供给 LLM，
                以生成更准确、更相关的回答。
                """;

            System.out.println("文档内容:\n" + documentText);

            // 3. 创建 RAG 助手
            System.out.println("\n3. 创建RAG助手...");
            RAGAssistant assistant = AiServices.builder(RAGAssistant.class)
                    .chatModel(chatModel)
                    .chatMemory(MessageWindowChatMemory.withMaxMessages(10))
                    .build();

            // 4. 测试问答
            System.out.println("\n4. 测试基于文档的问答:");
            System.out.println("--- 开始问答 ---\n");

            String[] testQuestions = {
                "什么是LangChain4j？",
                "RAG是什么意思？",
                "LangChain4j有哪些核心概念？"
            };

            for (String question : testQuestions) {
                System.out.println("问题: " + question);
                // 使用 RAG 方式回答
                String answer = performRAG(question, documentText, chatModel);
                System.out.println("回答: " + answer);
                System.out.println();
            }

            // 5. 演示从文件加载文档
            System.out.println("5. 演示从文件加载文档进行RAG:");

            // 创建示例文本文件
            Path tempFile = Paths.get("example_document.txt");
            String fileContent = """
                LangChain4j 餐饮智能助手使用指南

                本系统提供以下功能：
                1. 菜品咨询 - 询问菜品的成分、做法、口味等
                2. 订单查询 - 查询订单状态和物流信息
                3. 库存查询 - 查看门店菜品库存情况
                4. 退款申请 - 申请订单退款和售后

                常见问题：
                - 如何申请退款？答：提供订单号和退款原因即可
                - 订单多久能送达？答：一般情况下30-60分钟
                - 支持哪些支付方式？答：微信、支付宝、银行卡
                """;
            Files.writeString(tempFile, fileContent);

            // 加载文档
            TextDocumentParser parser = new TextDocumentParser();
            Document loadedDocument = parser.parse(Files.newInputStream(tempFile));
            String loadedText = loadedDocument.text();
            System.out.println("从文件加载的文档:\n" + loadedText);

            // 基于文件内容进行问答
            System.out.println("\n--- 基于文件内容进行RAG问答 ---\n");

            String[] fileQuestions = {
                "系统有哪些功能？",
                "如何申请退款？",
                "订单多久能送达？"
            };

            for (String question : fileQuestions) {
                System.out.println("问题: " + question);
                String answer = performRAG(question, loadedText, chatModel);
                System.out.println("回答: " + answer);
                System.out.println();
            }

            // 清理临时文件
            Files.deleteIfExists(tempFile);

        } catch (Exception e) {
            System.err.println("错误: " + e.getMessage());
            e.printStackTrace();
        }
    }
}
