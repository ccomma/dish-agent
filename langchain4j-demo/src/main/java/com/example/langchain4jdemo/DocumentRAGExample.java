package com.example.langchain4jdemo;

import dev.langchain4j.data.document.Document;
import dev.langchain4j.data.document.parser.TextDocumentParser;
import dev.langchain4j.memory.chat.MessageWindowChatMemory;
import dev.langchain4j.model.chat.ChatLanguageModel;
import dev.langchain4j.model.openai.OpenAiChatModel;
import dev.langchain4j.service.AiServices;
import dev.langchain4j.service.SystemMessage;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

/**
 * 文档加载和检索增强生成（RAG）示例
 * 演示如何加载文档并进行问答
 */
public class DocumentRAGExample {

    interface Assistant {
        @SystemMessage("你是一个有帮助的AI助手，基于提供的文档内容回答用户的问题。如果文档中没有相关信息，请说明你不知道。")
        String answer(String question);
    }

    public static void main(String[] args) {
        // 从配置文件加载配置
        Config config = Config.getInstance();

        System.out.println("=== 文档加载和RAG示例 ===");

        try {
            // 1. 创建聊天模型
            System.out.println("\n1. 创建Minimax聊天模型...");
            ChatLanguageModel chatModel = OpenAiChatModel.builder()
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

            // 3. 创建AI服务
            System.out.println("3. 创建AI助手服务...");
            Assistant assistant = AiServices.builder(Assistant.class)
                    .chatLanguageModel(chatModel)
                    .chatMemory(MessageWindowChatMemory.withMaxMessages(10))
                    .build();

            // 4. 测试问答
            System.out.println("\n4. 测试基于文档的问答:");
            System.out.println("文档内容:\n" + documentText);
            System.out.println("\n--- 开始问答 ---\n");

            String[] testQuestions = {
                "什么是LangChain4j？",
                "RAG是什么意思？",
                "LangChain4j有哪些核心概念？"
            };

            for (String question : testQuestions) {
                System.out.println("问题: " + question);
                String answer = assistant.answer(question);
                System.out.println("回答: " + answer);
                System.out.println();
            }

            // 5. 演示从文件加载文档
            System.out.println("5. 演示从文件加载文档:");

            // 创建示例文本文件
            Path tempFile = Paths.get("example_document.txt");
            Files.writeString(tempFile,
                "这是一个示例文档。\n" +
                "LangChain4j 支持从多种文件格式加载文档，包括 TXT、PDF、DOCX 等。\n" +
                "使用 FileSystemDocumentLoader 可以轻松地从文件系统加载文档。");

            TextDocumentParser parser = new TextDocumentParser();
            Document loadedDocument = parser.parse(Files.newInputStream(tempFile));
            System.out.println("从文件加载的文档: " + loadedDocument.text());

            // 清理临时文件
            Files.deleteIfExists(tempFile);

        } catch (Exception e) {
            System.err.println("错误: " + e.getMessage());
            e.printStackTrace();
        }
    }
}