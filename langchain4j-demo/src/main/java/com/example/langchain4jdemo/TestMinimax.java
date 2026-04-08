package com.example.langchain4jdemo;

import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.model.openai.OpenAiChatModel;

/**
 * Minimax 连接测试
 */
public class TestMinimax {

    public static void main(String[] args) {
        System.out.println("=== Minimax API 连接测试 ===\n");

        try {
            // 加载配置
            Config config = Config.getInstance();
            System.out.println("配置加载成功:");
            System.out.println("  BASE_URL: " + config.getBaseUrl());
            System.out.println("  MODEL: " + config.getModel());
            System.out.println("  API_KEY: " + (config.getApiKey().isEmpty() ? "未设置" : "已设置(" + config.getApiKey().substring(0, 8) + "...)"));

            // 创建模型
            ChatModel model = OpenAiChatModel.builder()
                    .apiKey(config.getApiKey())
                    .baseUrl(config.getBaseUrl())
                    .modelName(config.getModel())
                    .temperature(0.7)
                    .build();

            System.out.println("\n发送测试请求到 Minimax...");

            // 发送简单测试请求
            String response = model.chat("请回复'连接成功'，只需要回复这四个字");

            System.out.println("\n收到 Minimax 响应:");
            System.out.println("  " + response);

            if (response != null && !response.isEmpty()) {
                System.out.println("\n✓ Minimax API 连接测试成功！");
            } else {
                System.out.println("\n✗ 收到空响应，请检查 API 配置");
            }

        } catch (IllegalStateException e) {
            System.err.println("\n✗ 配置错误: " + e.getMessage());
        } catch (Exception e) {
            System.err.println("\n✗ 连接失败: " + e.getMessage());
            e.printStackTrace();
        }
    }
}