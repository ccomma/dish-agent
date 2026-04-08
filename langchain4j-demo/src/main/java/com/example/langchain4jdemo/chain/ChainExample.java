package com.example.langchain4jdemo.chain;

import com.example.langchain4jdemo.Config;
import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.model.openai.OpenAiChatModel;
import dev.langchain4j.model.chat.response.ChatResponse;

import java.util.List;
import java.util.function.Function;

/**
 * 链（Chain）组合示例
 * 演示如何将多个操作组合成流水线
 *
 * LangChain4j 0.31.0 中 Chain 通过函数式接口实现，
 * 本示例展示如何创建自定义处理链
 */
public class ChainExample {

    /**
     * 基础聊天链接口 - 定义链的基本行为
     */
    @FunctionalInterface
    interface LLMChain {
        String execute(String input);
    }

    /**
     * 翻译链实现
     */
    static class TranslationChain implements LLMChain {
        private final ChatModel model;
        private final String targetLanguage;

        TranslationChain(ChatModel model, String targetLanguage) {
            this.model = model;
            this.targetLanguage = targetLanguage;
        }

        @Override
        public String execute(String input) {
            String prompt = "将以下文本翻译成" + targetLanguage + "，只返回翻译结果：\n" + input;
            ChatResponse response = model.chat(List.of(UserMessage.from(prompt)));
            return response.aiMessage().text();
        }
    }

    /**
     * 摘要链实现
     */
    static class SummaryChain implements LLMChain {
        private final ChatModel model;

        SummaryChain(ChatModel model) {
            this.model = model;
        }

        @Override
        public String execute(String input) {
            String prompt = "为以下文本生成一个简短的摘要（不超过20字）：\n" + input;
            ChatResponse response = model.chat(List.of(UserMessage.from(prompt)));
            return response.aiMessage().text();
        }
    }

    /**
     * 情感分析链实现
     */
    static class SentimentChain implements LLMChain {
        private final ChatModel model;

        SentimentChain(ChatModel model) {
            this.model = model;
        }

        @Override
        public String execute(String input) {
            String prompt = "分析以下文本的情感，只返回 positive、negative 或 neutral 之一：\n" + input;
            ChatResponse response = model.chat(List.of(UserMessage.from(prompt)));
            return response.aiMessage().text().trim().toLowerCase();
        }
    }

    /**
     * 关键词提取链实现
     */
    static class KeywordChain implements LLMChain {
        private final ChatModel model;

        KeywordChain(ChatModel model) {
            this.model = model;
        }

        @Override
        public String execute(String input) {
            String prompt = "从以下文本中提取3个关键词或短语，用逗号分隔：\n" + input;
            ChatResponse response = model.chat(List.of(UserMessage.from(prompt)));
            return response.aiMessage().text().trim();
        }
    }

    /**
     * 链组合器 - 将多个链串联执行
     */
    static class ChainPipeline {
        private final LLMChain[] chains;

        ChainPipeline(LLMChain... chains) {
            this.chains = chains;
        }

        String execute(String input) {
            String current = input;
            for (int i = 0; i < chains.length; i++) {
                System.out.println("[Step " + (i + 1) + "] " + chains[i].getClass().getSimpleName() + ": " + current);
                current = chains[i].execute(current);
            }
            return current;
        }
    }

    /**
     * 条件路由链
     */
    static class ConditionalChain implements LLMChain {
        private final LLMChain positiveChain;
        private final LLMChain negativeChain;
        private final LLMChain sentimentChecker;

        ConditionalChain(ChatModel model) {
            this.sentimentChecker = new SentimentChain(model);
            this.positiveChain = new TranslationChain(model, "中文");
            this.negativeChain = new SummaryChain(model);
        }

        @Override
        public String execute(String input) {
            String sentiment = sentimentChecker.execute(input);
            System.out.println("[路由决策] 情感: " + sentiment);
            if (sentiment.contains("positive")) {
                return positiveChain.execute(input);
            } else {
                return negativeChain.execute(input);
            }
        }
    }

    public static void main(String[] args) {
        Config config = Config.getInstance();

        System.out.println("=== LangChain4j 链（Chain）组合示例 ===\n");

        try {
            // 创建聊天模型
            ChatModel model = OpenAiChatModel.builder()
                    .apiKey(config.getApiKey())
                    .baseUrl(config.getBaseUrl())
                    .modelName(config.getModel())
                    .temperature(0.3)
                    .build();

            // ═══════════════════════════════════════════════════════════
            // 示例1: 基本链组合 - 翻译 -> 摘要
            // ═══════════════════════════════════════════════════════════
            System.out.println("╔════════════════════════════════════════════════════╗");
            System.out.println("║ 示例1: 基本链组合 - 翻译 -> 摘要                ║");
            System.out.println("╚════════════════════════════════════════════════════╝\n");

            String input1 = "LangChain4j is a powerful Java framework for building AI-powered applications.";
            System.out.println("输入: " + input1 + "\n");

            ChainPipeline pipeline1 = new ChainPipeline(
                new TranslationChain(model, "中文"),
                new SummaryChain(model)
            );
            String result1 = pipeline1.execute(input1);
            System.out.println("\n[最终结果] " + result1);

            System.out.println("\n══════════════════════════════════════════════════════════\n");

            // ═══════════════════════════════════════════════════════════
            // 示例2: 情感分析 -> 路由
            // ═══════════════════════════════════════════════════════════
            System.out.println("╔════════════════════════════════════════════════════╗");
            System.out.println("║ 示例2: 条件路由链                                ║");
            System.out.println("╚════════════════════════════════════════════════════╝\n");

            String input2 = "I love this product! It's absolutely amazing!";
            System.out.println("输入: " + input2 + "\n");

            ConditionalChain conditionalChain = new ConditionalChain(model);
            String result2 = conditionalChain.execute(input2);
            System.out.println("\n[最终结果] " + result2);

            System.out.println("\n══════════════════════════════════════════════════════════\n");

            // ═══════════════════════════════════════════════════════════
            // 示例3: 多步骤处理流水线
            // ═══════════════════════════════════════════════════════════
            System.out.println("╔════════════════════════════════════════════════════╗");
            System.out.println("║ 示例3: 多步骤处理流水线                          ║");
            System.out.println("╚════════════════════════════════════════════════════╝\n");

            String input3 = "The new AI framework is revolutionary and changes everything!";
            System.out.println("输入: " + input3 + "\n");

            ChainPipeline pipeline3 = new ChainPipeline(
                new SentimentChain(model),
                new KeywordChain(model),
                new SummaryChain(model)
            );
            String result3 = pipeline3.execute(input3);
            System.out.println("\n[最终结果] " + result3);

            System.out.println("\n══════════════════════════════════════════════════════════\n");

            // ═══════════════════════════════════════════════════════════
            // 示例4: 完整 RAG 链
            // ═══════════════════════════════════════════════════════════
            System.out.println("╔════════════════════════════════════════════════════╗");
            System.out.println("║ 示例4: 完整 RAG 检索链                          ║");
            System.out.println("╚════════════════════════════════════════════════════╝\n");

            String document = """
                LangChain4j 核心概念：
                1. Models - 与 LLM 交互的接口
                2. Prompts - 提示模板管理
                3. Memory - 对话状态保持
                4. Chains - 多步骤处理序列
                5. Agents - 使用工具的 AI 实体
                """;

            String query = "LangChain4j 有哪些核心概念？";
            System.out.println("文档: " + document.replace("\n", " "));
            System.out.println("问题: " + query + "\n");

            // RAG 流程：检索 -> 增强 -> 生成
            String retrieved = retrieve(document, query);
            System.out.println("[Step 1] 检索: " + retrieved);

            String augmented = augment(query, retrieved);
            System.out.println("[Step 2] 增强: " + augmented);

            ChatResponse generated = model.chat(List.of(UserMessage.from(augmented)));
            System.out.println("[Step 3] 生成: " + generated.aiMessage().text());

            System.out.println("\n══════════════════════════════════════════════════════════\n");

            // ═══════════════════════════════════════════════════════════
            // 示例5: 函数式 Chain 组合
            // ═══════════════════════════════════════════════════════════
            System.out.println("╔════════════════════════════════════════════════════╗");
            System.out.println("║ 示例5: 函数式 Chain 组合                        ║");
            System.out.println("╚════════════════════════════════════════════════════╝\n");

            // 使用函数式接口组合链
            Function<String, String> step1 = input -> new TranslationChain(model, "日文").execute(input);
            Function<String, String> step2 = input -> new SummaryChain(model).execute(input);

            String input5 = "LangChain4j makes AI development in Java simple and powerful!";
            System.out.println("输入: " + input5 + "\n");

            String result5 = step1.andThen(step2).apply(input5);
            System.out.println("\n[最终结果] " + result5);

        } catch (Exception e) {
            System.err.println("错误: " + e.getMessage());
            e.printStackTrace();
        }
    }

    // 简化的检索方法（实际应用中应使用向量数据库）
    static String retrieve(String document, String query) {
        if (query.contains("概念")) {
            return "LangChain4j 核心概念包括：1. Models 2. Prompts 3. Memory 4. Chains 5. Agents";
        }
        return document;
    }

    // RAG 增强 - 将检索结果与问题组合
    static String augment(String query, String retrievedContext) {
        return "基于以下信息回答问题。\n\n【相关信息】\n" + retrievedContext +
               "\n\n【问题】" + query + "\n\n请根据提供的信息回答。";
    }
}
