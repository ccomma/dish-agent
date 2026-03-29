package com.example.langchain4jdemo;

import dev.langchain4j.model.chat.ChatLanguageModel;
import dev.langchain4j.model.ollama.OllamaChatModel;
import dev.langchain4j.service.AiServices;
import dev.langchain4j.service.SystemMessage;

/**
 * 本地模型集成示例（使用Ollama）
 * 演示如何与本地运行的模型（如Llama2、Mistral等）交互
 * 无需API密钥，可以在本地环境运行
 *
 * 前提条件：
 * 1. 安装并运行Ollama：https://ollama.ai/
 * 2. 拉取模型：ollama pull llama2 或 ollama pull mistral
 */
public class LocalModelExample {

    // 定义AI助手接口
    interface LocalAssistant {
        @SystemMessage("你是一个有帮助的AI助手，使用中文回答用户的问题。")
        String chat(String message);
    }

    public static void main(String[] args) {
        System.out.println("=== 本地模型集成示例（Ollama） ===");
        System.out.println("本示例使用本地运行的Ollama服务\n");

        // 检查Ollama服务是否可用
        if (!isOllamaRunning()) {
            System.out.println("警告：Ollama服务可能未运行");
            System.out.println("请确保：");
            System.out.println("1. 已安装Ollama：https://ollama.ai/");
            System.out.println("2. 已启动Ollama服务");
            System.out.println("3. 已拉取模型：ollama pull llama2");
            System.out.println("\n将尝试继续运行，但可能会失败...\n");
        }

        try {
            // 示例1: 使用Llama2模型
            System.out.println("1. 使用Llama2模型:");

            ChatLanguageModel llamaModel = OllamaChatModel.builder()
                    .baseUrl("http://localhost:11434")
                    .modelName("llama2")
                    .temperature(0.7)
                    .build();

            LocalAssistant llamaAssistant = AiServices.builder(LocalAssistant.class)
                    .chatLanguageModel(llamaModel)
                    .build();

            System.out.println("用户: 用中文介绍一下你自己");
            String response = llamaAssistant.chat("用中文介绍一下你自己");
            System.out.println("AI: " + response);
            System.out.println();

            // 示例2: 使用Mistral模型（如果可用）
            System.out.println("2. 使用Mistral模型:");

            try {
                ChatLanguageModel mistralModel = OllamaChatModel.builder()
                        .baseUrl("http://localhost:11434")
                        .modelName("mistral")
                        .temperature(0.7)
                        .build();

                LocalAssistant mistralAssistant = AiServices.builder(LocalAssistant.class)
                        .chatLanguageModel(mistralModel)
                        .build();

                System.out.println("用户: 解释一下人工智能的主要应用领域");
                response = mistralAssistant.chat("解释一下人工智能的主要应用领域");
                System.out.println("AI: " + response);
                System.out.println();
            } catch (Exception e) {
                System.out.println("Mistral模型不可用: " + e.getMessage());
                System.out.println("请运行: ollama pull mistral");
                System.out.println();
            }

            // 示例3: 简单的对话循环
            System.out.println("3. 对话演示（输入 'exit' 退出）:");

            ChatLanguageModel model = OllamaChatModel.builder()
                    .baseUrl("http://localhost:11434")
                    .modelName("llama2") // 可以使用其他模型如 "mistral", "codellama"
                    .temperature(0.7)
                    .maxRetries(2)
                    .build();

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
                    String aiResponse = model.generate(userInput);
                    System.out.println("AI: " + aiResponse);
                    System.out.println();
                } catch (Exception e) {
                    System.err.println("错误: " + e.getMessage());
                    System.out.println("请检查：");
                    System.out.println("1. Ollama服务是否运行：ollama serve");
                    System.out.println("2. 模型是否已拉取：ollama pull llama2");
                    break;
                }
            }

            scanner.close();

            // 示例4: 代码生成演示
            System.out.println("\n4. 代码生成演示:");

            ChatLanguageModel codeModel = OllamaChatModel.builder()
                    .baseUrl("http://localhost:11434")
                    .modelName("codellama") // 代码专用模型
                    .temperature(0.3) // 较低温度以获得更确定的代码
                    .build();

            if (isModelAvailable("codellama")) {
                System.out.println("用户: 写一个Java函数计算斐波那契数列");
                String codeResponse = codeModel.generate("写一个Java函数计算斐波那契数列");
                System.out.println("AI: " + codeResponse);
            } else {
                System.out.println("CodeLlama模型不可用，请运行: ollama pull codellama");
                // 使用llama2作为备选
                System.out.println("用户: 写一个Java函数计算斐波那契数列");
                String codeResponse = model.generate("写一个Java函数计算斐波那契数列");
                System.out.println("AI: " + codeResponse);
            }

        } catch (Exception e) {
            System.err.println("错误: " + e.getMessage());
            System.out.println("\n故障排除:");
            System.out.println("1. 安装Ollama: https://ollama.ai/");
            System.out.println("2. 启动Ollama服务: ollama serve");
            System.out.println("3. 拉取模型: ollama pull llama2");
            System.out.println("4. 验证服务: curl http://localhost:11434/api/tags");
        }
    }

    private static boolean isOllamaRunning() {
        try {
            java.net.HttpURLConnection connection = (java.net.HttpURLConnection)
                    new java.net.URL("http://localhost:11434/api/tags").openConnection();
            connection.setRequestMethod("GET");
            connection.setConnectTimeout(3000);
            connection.setReadTimeout(3000);
            int responseCode = connection.getResponseCode();
            return responseCode == 200;
        } catch (Exception e) {
            return false;
        }
    }

    private static boolean isModelAvailable(String modelName) {
        try {
            java.net.HttpURLConnection connection = (java.net.HttpURLConnection)
                    new java.net.URL("http://localhost:11434/api/tags").openConnection();
            connection.setRequestMethod("GET");
            connection.setConnectTimeout(3000);
            connection.setReadTimeout(3000);

            if (connection.getResponseCode() == 200) {
                String response = new java.util.Scanner(connection.getInputStream()).useDelimiter("\\A").next();
                return response.contains(modelName);
            }
            return false;
        } catch (Exception e) {
            return false;
        }
    }
}