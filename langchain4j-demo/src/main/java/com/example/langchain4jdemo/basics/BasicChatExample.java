package com.example.langchain4jdemo.basics;

import com.example.langchain4jdemo.Config;
import dev.langchain4j.model.chat.ChatLanguageModel;
import dev.langchain4j.model.openai.OpenAiChatModel;

/**
 * 基础对话示例
 * 演示如何使用LangChain4j与Minimax模型进行简单对话
 *
 * 配置：config.properties 文件
 */
public class BasicChatExample {

    public static void main(String[] args) {
        // 从配置文件加载配置
        Config config = Config.getInstance();

        // 创建Minimax聊天模型
        ChatLanguageModel model = OpenAiChatModel.builder()
                .apiKey(config.getApiKey())
                .baseUrl(config.getBaseUrl())
                .modelName(config.getModel())
                .temperature(0.7)
                .maxTokens(300)
                .build();

        System.out.println("=== LangChain4j 基础对话示例 ===");
        System.out.println("模型: " + config.getModel());
        System.out.println("输入 'exit' 退出对话\n");

        // 简单的对话循环
        java.util.Scanner scanner = new java.util.Scanner(System.in);
        while (true) {
            System.out.print("用户: ");
            String userInput = scanner.nextLine().trim();

            if (userInput.equalsIgnoreCase("exit")) {
                System.out.println("对话结束");
                break;
            }

            if (userInput.isEmpty()) {
                continue;
            }

            try {
                // 发送消息到模型并获取响应
                String response = model.generate(userInput);
                System.out.println("AI: " + response);
                System.out.println();
            } catch (Exception e) {
                System.err.println("错误: " + e.getMessage());
                e.printStackTrace();
            }
        }

        scanner.close();
    }
}