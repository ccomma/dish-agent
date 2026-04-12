package com.example.langchain4jdemo.streaming;

import com.example.langchain4jdemo.Config;
import dev.langchain4j.data.message.ChatMessage;
import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.model.chat.StreamingChatModel;
import dev.langchain4j.model.chat.response.ChatResponse;
import dev.langchain4j.model.chat.response.StreamingChatResponseHandler;
import dev.langchain4j.model.openai.OpenAiStreamingChatModel;

import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

/**
 * 真正的流式输出示例
 * 演示 LangChain4j 1.x StreamingChatModel API
 *
 * LangChain4j 1.x 流式 API：
 * - StreamingChatModel: 支持流式输出的 ChatModel
 * - StreamingChatResponseHandler: 处理流式响应的回调接口
 */
public class StreamingExample {

    public static void main(String[] args) {
        Config config = Config.getInstance();

        System.out.println("=== LangChain4j 流式输出示例 ===\n");

        try {
            // 创建流式聊天模型
            StreamingChatModel streamingModel = OpenAiStreamingChatModel.builder()
                    .apiKey(config.getApiKey())
                    .baseUrl(config.getBaseUrl())
                    .modelName(config.getModel())
                    .temperature(0.7)
                    .build();

            // ═══════════════════════════════════════════════════════════
            // 示例1: 真正的流式输出
            // ═══════════════════════════════════════════════════════════
            System.out.println("╔════════════════════════════════════════════════════╗");
            System.out.println("║ 示例1: 真正的流式输出                          ║");
            System.out.println("╚════════════════════════════════════════════════════╝\n");

            String userMessage = "用一句话介绍 LangChain4j";
            System.out.println("用户: " + userMessage);
            System.out.print("AI: ");

            // 使用 CountDownLatch 等待流式响应完成
            CountDownLatch latch = new CountDownLatch(1);

            // 定义流式响应处理器
            StreamingChatResponseHandler handler = new StreamingChatResponseHandler() {
                @Override
                public void onPartialResponse(String partialResponse) {
                    // 每个流式片段到达时调用
                    System.out.print(partialResponse);
                }

                @Override
                public void onCompleteResponse(ChatResponse response) {
                    // 流式响应完成时调用
                    System.out.println("\n\n[流式响应完成]");
                    if (response != null && response.aiMessage() != null) {
                        System.out.println("完整内容: " + response.aiMessage().text());
                    }
                    latch.countDown();
                }

                @Override
                public void onError(Throwable error) {
                    System.err.println("\n错误: " + error.getMessage());
                    latch.countDown();
                }
            };

            // 发起流式请求（异步执行）
            streamingModel.chat(userMessage, handler);

            // 等待流式响应完成（最多30秒）
            latch.await(30, TimeUnit.SECONDS);

            System.out.println("\n══════════════════════════════════════════════════════════\n");

            // ═══════════════════════════════════════════════════════════
            // 示例2: 带消息历史的流式对话
            // ═══════════════════════════════════════════════════════════
            System.out.println("╔════════════════════════════════════════════════════╗");
            System.out.println("║ 示例2: 带消息历史的流式对话                    ║");
            System.out.println("╚════════════════════════════════════════════════════╝\n");

            CountDownLatch latch2 = new CountDownLatch(1);

            List<ChatMessage> messages = List.of(
                    UserMessage.from("什么是 Java 17 的新特性？")
            );

            System.out.println("用户: 什么是 Java 17 的新特性？");
            System.out.print("AI: ");

            streamingModel.chat(messages, new StreamingChatResponseHandler() {
                @Override
                public void onPartialResponse(String partialResponse) {
                    System.out.print(partialResponse);
                }

                @Override
                public void onCompleteResponse(ChatResponse response) {
                    System.out.println("\n\n[流式响应完成]");
                    latch2.countDown();
                }

                @Override
                public void onError(Throwable error) {
                    System.err.println("\n错误: " + error.getMessage());
                    latch2.countDown();
                }
            });

            latch2.await(30, TimeUnit.SECONDS);

            System.out.println("\n══════════════════════════════════════════════════════════\n");

            // ═══════════════════════════════════════════════════════════
            // 示例3: 使用 CompletableFuture 实现非阻塞流式
            // ═══════════════════════════════════════════════════════════
            System.out.println("╔════════════════════════════════════════════════════╗");
            System.out.println("║ 示例3: 非阻塞流式调用                          ║");
            System.out.println("╚════════════════════════════════════════════════════╝\n");

            CompletableFuture<String> streamingFuture = new CompletableFuture<>();
            StringBuilder fullResponse = new StringBuilder();

            String query = "解释一下什么是 RAG";
            System.out.println("用户: " + query);
            System.out.print("AI: ");

            streamingModel.chat(query, new StreamingChatResponseHandler() {
                @Override
                public void onPartialResponse(String partialResponse) {
                    fullResponse.append(partialResponse);
                    System.out.print(partialResponse);
                }

                @Override
                public void onCompleteResponse(ChatResponse response) {
                    streamingFuture.complete(fullResponse.toString());
                }

                @Override
                public void onError(Throwable error) {
                    streamingFuture.completeExceptionally(error);
                }
            });

            // 可以在等待时做其他事情
            System.out.println("\n\n[主线程可以同时执行其他任务...]");

            // 阻塞等待结果
            try {
                String result = streamingFuture.get(30, TimeUnit.SECONDS);
                System.out.println("\n[异步任务完成，最终结果长度: " + result.length() + " 字符]");
            } catch (Exception e) {
                System.err.println("获取流式结果失败: " + e.getMessage());
            }

            System.out.println("\n══════════════════════════════════════════════════════════\n");
            System.out.println("【LangChain4j 流式 API 说明】");
            System.out.println("1. 使用 StreamingChatModel 接口（替代 ChatModel）");
            System.out.println("2. 调用 .chat(message, handler) 方法");
            System.out.println("3. StreamingChatResponseHandler 提供三个回调：");
            System.out.println("   - onPartialResponse(): 每个片段到达时调用");
            System.out.println("   - onCompleteResponse(): 响应完成时调用");
            System.out.println("   - onError(): 错误发生时调用");
            System.out.println("\n注意：需要模型提供商支持流式输出 API");

        } catch (Exception e) {
            System.err.println("错误: " + e.getMessage());
            e.printStackTrace();
        }
    }
}
