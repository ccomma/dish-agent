package com.example.langchain4jdemo.streaming;

import com.example.langchain4jdemo.Config;
import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.model.openai.OpenAiChatModel;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

/**
 * 流式输出概念示例
 * 演示如何实现类似流式输出的效果
 *
 * 注意：真正的流式输出需要模型提供商支持 Streaming API。
 * 本示例使用 CompletableFuture 模拟非阻塞效果。
 */
public class StreamingExample {

    public static void main(String[] args) {
        Config config = Config.getInstance();

        System.out.println("=== LangChain4j 流式输出概念示例 ===\n");

        try {
            ChatModel model = OpenAiChatModel.builder()
                    .apiKey(config.getApiKey())
                    .baseUrl(config.getBaseUrl())
                    .modelName(config.getModel())
                    .temperature(0.7)
                    .build();

            // ═══════════════════════════════════════════════════════════
            // 示例1: 非阻塞调用（模拟流式效果）
            // ═══════════════════════════════════════════════════════════
            System.out.println("╔════════════════════════════════════════════════════╗");
            System.out.println("║ 示例1: 非阻塞调用（模拟流式）                  ║");
            System.out.println("╚════════════════════════════════════════════════════╝\n");

            String userMessage = "用一句话介绍 LangChain4j";
            System.out.println("用户: " + userMessage);
            System.out.print("AI: ");

            // 使用 CompletableFuture 模拟异步流式效果
            CompletableFuture<String> futureResponse = CompletableFuture.supplyAsync(() -> {
                try {
                    // 模拟流式输出的逐字显示效果
                    String response = model.chat(userMessage);
                    // 简单模拟：分批返回
                    return response;
                } catch (Exception e) {
                    return "错误: " + e.getMessage();
                }
            });

            // 显示加载指示器
            while (!futureResponse.isDone()) {
                System.out.print("█");
                try {
                    Thread.sleep(100);
                } catch (InterruptedException e) {
                    break;
                }
            }
            System.out.println();

            // 获取结果
            String result = futureResponse.get(30, TimeUnit.SECONDS);
            System.out.println(result);

            System.out.println("\n══════════════════════════════════════════════════════════\n");

            // ═══════════════════════════════════════════════════════════
            // 示例2: 传统阻塞调用对比
            // ═══════════════════════════════════════════════════════════
            System.out.println("╔════════════════════════════════════════════════════╗");
            System.out.println("║ 示例2: 阻塞调用对比                            ║");
            System.out.println("╚════════════════════════════════════════════════════╝\n");

            System.out.println("【阻塞版本】- 等待完整响应后再显示\n");

            String query = "解释什么是递归";
            System.out.println("用户: " + query);
            System.out.print("AI: ");

            long start = System.currentTimeMillis();
            String response = model.chat(query);
            long elapsed = System.currentTimeMillis() - start;

            System.out.println(response);
            System.out.println("\n耗时: " + elapsed + "ms");

            System.out.println("\n【对比】");
            System.out.println("阻塞调用: 等到全部生成完成后一次性显示");
            System.out.println("流式调用: 边生成边显示，用户体验更好（需要底层支持）");

            System.out.println("\n══════════════════════════════════════════════════════════\n");

            // ═══════════════════════════════════════════════════════════
            // 示例3: 多线程并行请求
            // ═══════════════════════════════════════════════════════════
            System.out.println("╔════════════════════════════════════════════════════╗");
            System.out.println("║ 示例3: 多线程并行请求                          ║");
            System.out.println("╚════════════════════════════════════════════════════╝\n");

            String[] queries = {
                "Java 是什么？",
                "Python 是什么？",
                "JavaScript 是什么？"
            };

            System.out.println("并行提问 " + queries.length + " 个问题...\n");

            long multiStart = System.currentTimeMillis();

            // 并行执行
            CompletableFuture<String>[] futures = new CompletableFuture[queries.length];
            for (int i = 0; i < queries.length; i++) {
                final int idx = i;
                futures[i] = CompletableFuture.supplyAsync(() -> {
                    try {
                        return model.chat(queries[idx]);
                    } catch (Exception e) {
                        return "错误: " + e.getMessage();
                    }
                });
            }

            // 等待所有完成
            CompletableFuture.allOf(futures).join();
            long multiElapsed = System.currentTimeMillis() - multiStart;

            // 收集结果
            for (int i = 0; i < queries.length; i++) {
                System.out.println("问题 " + (i + 1) + ": " + queries[i]);
                System.out.println("回答: " + futures[i].get());
                System.out.println();
            }

            System.out.println("总耗时: " + multiElapsed + "ms（并行） vs "
                    + (multiElapsed * queries.length) + "ms（串行估计）");

            System.out.println("\n══════════════════════════════════════════════════════════\n");
            System.out.println("示例完成！");
            System.out.println("\n【流式输出说明】");
            System.out.println("真正的流式输出需要：");
            System.out.println("1. 模型提供商支持 Streaming API");
            System.out.println("2. 使用 StreamingChatLanguageModel 接口");
            System.out.println("3. 实现 StreamingResponseHandler 回调");

        } catch (Exception e) {
            System.err.println("错误: " + e.getMessage());
            e.printStackTrace();
        }
    }
}
