package com.example.langchain4jdemo;

import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.data.message.ChatMessage;
import dev.langchain4j.data.message.SystemMessage;
import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.model.chat.ChatLanguageModel;
import dev.langchain4j.model.openai.OpenAiChatModel;
import dev.langchain4j.model.output.Response;

import java.util.Arrays;
import java.util.List;

/**
 * 提示模板和系统消息示例
 * 演示如何使用系统消息、用户消息和消息历史
 */
public class PromptTemplateExample {

    public static void main(String[] args) {
        // 从配置文件加载配置
        Config config = Config.getInstance();

        // 创建Minimax聊天模型
        ChatLanguageModel model = OpenAiChatModel.builder()
                .apiKey(config.getApiKey())
                .baseUrl(config.getBaseUrl())
                .modelName(config.getModel())
                .temperature(0.7)
                .build();

        System.out.println("=== 提示模板和系统消息示例 ===");

        // 示例1: 使用系统消息定义AI角色
        System.out.println("\n1. 使用系统消息定义AI角色:");

        String systemMessage = "你是一个有帮助的Java编程助手。你专门帮助开发人员解决Java相关的问题，"
                + "并提供代码示例和最佳实践。你的回答应该简洁明了。";

        String userMessage = "请解释Java中的多态性，并给出一个简单的示例。";

        // 构建消息列表
        List<ChatMessage> messages = Arrays.asList(
                SystemMessage.from(systemMessage),
                UserMessage.from(userMessage)
        );

        Response<AiMessage> response = model.generate(messages);
        System.out.println("系统消息: " + systemMessage);
        System.out.println("用户消息: " + userMessage);
        System.out.println("AI响应: " + response.content().text());
        System.out.println("Tokens使用: " + response.tokenUsage());

        // 示例2: 使用提示模板
        System.out.println("\n2. 使用提示模板:");

        String template = "请将以下英语文本翻译成中文:\n{{text}}";
        String englishText = "Hello, welcome to LangChain4j tutorial. "
                + "This framework helps you build AI-powered applications in Java.";

        // 简单的模板替换
        String filledPrompt = template.replace("{{text}}", englishText);

        String translation = model.generate(filledPrompt);
        System.out.println("原始模板: " + template);
        System.out.println("填充后的提示: " + filledPrompt);
        System.out.println("翻译结果: " + translation);

        // 示例3: 对话历史
        System.out.println("\n3. 对话历史示例:");

        List<ChatMessage> conversation = Arrays.asList(
                SystemMessage.from("你是一个友好的聊天机器人。"),
                UserMessage.from("你好！"),
                AiMessage.from("你好！我是AI助手，有什么可以帮你的吗？"),
                UserMessage.from("你能告诉我今天的天气如何吗？")
        );

        Response<AiMessage> conversationResponse = model.generate(conversation);
        System.out.println("对话历史:");
        conversation.forEach(msg -> System.out.println("  " + msg.type() + ": " + msg.text()));
        System.out.println("最新响应: " + conversationResponse.content().text());
    }
}