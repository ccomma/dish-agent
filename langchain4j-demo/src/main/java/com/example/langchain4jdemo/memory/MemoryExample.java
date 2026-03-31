package com.example.langchain4jdemo.memory;

import com.example.langchain4jdemo.Config;
import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.data.message.ChatMessage;
import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.memory.ChatMemory;
import dev.langchain4j.memory.chat.MessageWindowChatMemory;
import dev.langchain4j.model.chat.ChatLanguageModel;
import dev.langchain4j.model.openai.OpenAiChatModel;
import dev.langchain4j.model.output.Response;
import dev.langchain4j.service.AiServices;
import dev.langchain4j.service.SystemMessage;

import java.util.List;
import java.util.Scanner;

/**
 * 记忆（Memory）示例
 * 演示 LangChain4j 中不同类型的 ChatMemory 及使用场景
 *
 * LangChain4j 提供了多种 ChatMemory 实现：
 * - MessageWindowChatMemory: 保留固定数量的消息（推荐）
 * - TokenWindowChatMemory: 保留固定数量的 token（需要 tokenizer）
 * - VectorStoreChatMemory: 基于向量存储的记忆（支持更长上下文）
 */
public class MemoryExample {

    // AI 助手接口
    interface Assistant {
        @SystemMessage("你是一个友好的对话助手，能够记住之前的对话内容。")
        String chat(String userMessage);
    }

    // ═══════════════════════════════════════════════════════════════════
    // 示例1: MessageWindowChatMemory - 固定消息数量
    // ═══════════════════════════════════════════════════════════════════
    static void example1MessageWindowMemory(Config config) {
        System.out.println("╔════════════════════════════════════════════════════╗");
        System.out.println("║ 示例1: MessageWindowChatMemory                    ║");
        System.out.println("╚════════════════════════════════════════════════════╝\n");

        // 限制最多保留 5 条消息
        ChatMemory memory = MessageWindowChatMemory.withMaxMessages(5);

        ChatLanguageModel model = OpenAiChatModel.builder()
                .apiKey(config.getApiKey())
                .baseUrl(config.getBaseUrl())
                .modelName(config.getModel())
                .temperature(0.7)
                .build();

        Assistant assistant = AiServices.builder(Assistant.class)
                .chatLanguageModel(model)
                .chatMemory(memory)
                .build();

        System.out.println("【Memory 配置】保留最近 5 条消息\n");

        // 对话 1
        String response1 = assistant.chat("我叫张三");
        System.out.println("用户: 我叫张三");
        System.out.println("AI: " + response1);
        System.out.println("Memory 大小: " + memory.messages().size());

        // 对话 2
        String response2 = assistant.chat("我几岁？");
        System.out.println("\n用户: 我几岁？");
        System.out.println("AI: " + response2);

        // 对话 3 - 关于名字
        String response3 = assistant.chat("你还记得我叫什么吗？");
        System.out.println("\n用户: 你还记得我叫什么吗？");
        System.out.println("AI: " + response3);

        // 对话 4-6 - 超出限制
        assistant.chat("我喜欢吃苹果");
        assistant.chat("我喜欢喝咖啡");
        assistant.chat("我喜欢看书");
        System.out.println("\n[又进行了3轮对话后]");
        System.out.println("Memory 大小: " + memory.messages().size());

        // 再问名字 - 可能会忘记
        String response7 = assistant.chat("我叫什么名字？");
        System.out.println("\n用户: 我叫什么名字？");
        System.out.println("AI: " + response7);
        System.out.println("(因为超过了5条消息限制，早期的名字可能已被遗忘)\n");
    }

    // ═══════════════════════════════════════════════════════════════════
    // 示例2: 长对话记忆
    // ═══════════════════════════════════════════════════════════════════
    static void example2LongConversation(Config config) {
        System.out.println("\n══════════════════════════════════════════════════════════\n");
        System.out.println("╔════════════════════════════════════════════════════╗");
        System.out.println("║ 示例2: 长对话记忆                              ║");
        System.out.println("╚════════════════════════════════════════════════════╝\n");

        // 限制最多 10 条消息
        ChatMemory memory = MessageWindowChatMemory.withMaxMessages(10);

        ChatLanguageModel model = OpenAiChatModel.builder()
                .apiKey(config.getApiKey())
                .baseUrl(config.getBaseUrl())
                .modelName(config.getModel())
                .temperature(0.7)
                .build();

        Assistant assistant = AiServices.builder(Assistant.class)
                .chatLanguageModel(model)
                .chatMemory(memory)
                .build();

        System.out.println("【Memory 配置】保留最近 500 tokens\n");

        String[] messages = {
            "我的名字是李四",
            "我是一名软件工程师",
            "我在北京工作",
            "我喜欢编程",
            "我会 Java、Python 和 JavaScript",
            "我每天写代码 8 小时"
        };

        for (String msg : messages) {
            String response = assistant.chat(msg);
            System.out.println("用户: " + msg);
            System.out.println("AI: " + response);
            System.out.println("Memory tokens: " + "[已记录]");
            System.out.println();
        }

        // 检查是否能记住
        String whoAmI = assistant.chat("请总结我的个人信息");
        System.out.println("\n用户: 请总结我的个人信息");
        System.out.println("AI: " + whoAmI);
    }

    // ═══════════════════════════════════════════════════════════════════
    // 示例3: 手动管理 Memory
    // ═══════════════════════════════════════════════════════════════════
    static void example3ManualMemory(Config config) {
        System.out.println("\n══════════════════════════════════════════════════════════\n");
        System.out.println("╔════════════════════════════════════════════════════╗");
        System.out.println("║ 示例3: 手动管理 Memory                          ║");
        System.out.println("╚════════════════════════════════════════════════════╝\n");

        ChatMemory memory = MessageWindowChatMemory.withMaxMessages(10);

        ChatLanguageModel model = OpenAiChatModel.builder()
                .apiKey(config.getApiKey())
                .baseUrl(config.getBaseUrl())
                .modelName(config.getModel())
                .temperature(0.7)
                .build();

        System.out.println("【手动管理 Memory】\n");

        // 手动添加消息
        memory.add(UserMessage.from("你好，我是王五"));
        System.out.println("手动添加 UserMessage: 你好，我是王五");

        memory.add(AiMessage.from("你好王五！很高兴认识你。有什么我可以帮你的吗？"));
        System.out.println("手动添加 AiMessage: 你好王五！很高兴认识你。\n");

        // 手动清除记忆
        System.out.println("清除 Memory...");
        memory.clear();
        System.out.println("Memory 大小: " + memory.messages().size());

        // 对比
        String response = model.generate(
            List.of(UserMessage.from("你还记得我吗？"))
        ).content().text();
        System.out.println("\nAI: " + response);
        System.out.println("(Memory 已清空，AI 不记得之前的内容)\n");
    }

    // ═══════════════════════════════════════════════════════════════════
    // 示例4: 多会话 Memory 隔离
    // ═══════════════════════════════════════════════════════════════════
    static void example4MultiSession(Config config) {
        System.out.println("\n══════════════════════════════════════════════════════════\n");
        System.out.println("╔════════════════════════════════════════════════════╗");
        System.out.println("║ 示例4: 多会话 Memory 隔离                        ║");
        System.out.println("╚════════════════════════════════════════════════════╝\n");

        ChatLanguageModel model = OpenAiChatModel.builder()
                .apiKey(config.getApiKey())
                .baseUrl(config.getBaseUrl())
                .modelName(config.getModel())
                .temperature(0.7)
                .build();

        // 为不同用户创建独立的 Memory
        ChatMemory userAMemory = MessageWindowChatMemory.withMaxMessages(10);
        ChatMemory userBMemory = MessageWindowChatMemory.withMaxMessages(10);

        Assistant assistantA = AiServices.builder(Assistant.class)
                .chatLanguageModel(model)
                .chatMemory(userAMemory)
                .build();

        Assistant assistantB = AiServices.builder(Assistant.class)
                .chatLanguageModel(model)
                .chatMemory(userBMemory)
                .build();

        System.out.println("【用户 A 对话】");
        assistantA.chat("我叫张三");
        String aName = assistantA.chat("我叫什么？");
        System.out.println("用户 A 问：我叫什么？");
        System.out.println("AI 回复： " + aName);

        System.out.println("\n【用户 B 对话】");
        assistantB.chat("我是李四");
        String bName = assistantB.chat("我叫什么？");
        System.out.println("用户 B 问：我叫什么？");
        System.out.println("AI 回复： " + bName);

        System.out.println("\n【验证 Memory 隔离】");
        System.out.println("用户 A 再问：我叫什么？");
        String aName2 = assistantA.chat("我叫什么？");
        System.out.println("AI 回复： " + aName2);
        System.out.println("(用户 A 应该记得自己是张三)\n");
    }

    // ═══════════════════════════════════════════════════════════════════
    // 示例5: 对话历史查看
    // ═══════════════════════════════════════════════════════════════════
    static void example5ViewHistory(Config config) {
        System.out.println("\n══════════════════════════════════════════════════════════\n");
        System.out.println("╔════════════════════════════════════════════════════╗");
        System.out.println("║ 示例5: 查看对话历史                              ║");
        System.out.println("╚════════════════════════════════════════════════════╝\n");

        ChatMemory memory = MessageWindowChatMemory.withMaxMessages(20);

        ChatLanguageModel model = OpenAiChatModel.builder()
                .apiKey(config.getApiKey())
                .baseUrl(config.getBaseUrl())
                .modelName(config.getModel())
                .temperature(0.7)
                .build();

        Assistant assistant = AiServices.builder(Assistant.class)
                .chatLanguageModel(model)
                .chatMemory(memory)
                .build();

        // 进行几轮对话
        assistant.chat("你好");
        assistant.chat("我叫赵六");
        assistant.chat("我是工程师");

        System.out.println("【对话历史】\n");

        List<ChatMessage> history = memory.messages();
        for (int i = 0; i < history.size(); i++) {
            ChatMessage msg = history.get(i);
            String type = msg.type().toString();
            String content = msg.text();
            System.out.println("[" + (i + 1) + "] " + type + ": " + content);
        }

        System.out.println("\n【作为上下文传递给模型】");
        System.out.println("将以上 " + history.size() + " 条消息作为上下文...\n");

        // 基于历史继续对话
        String response = assistant.chat("根据对话历史，我是什么职业？");
        System.out.println("用户: 根据对话历史，我是什么职业？");
        System.out.println("AI: " + response);
    }

    public static void main(String[] args) {
        Config config = Config.getInstance();

        System.out.println("=== LangChain4j 记忆（Memory）示例 ===\n");

        try {
            example1MessageWindowMemory(config);
            example2LongConversation(config);
            example3ManualMemory(config);
            example4MultiSession(config);
            example5ViewHistory(config);

            System.out.println("\n══════════════════════════════════════════════════════════\n");
            System.out.println("示例完成！");
            System.out.println("\n【Memory 类型选择指南】");
            System.out.println("- MessageWindowChatMemory: 简单场景，固定消息数量（推荐）");
            System.out.println("- TokenWindowChatMemory: 需要精确控制 token 数量（需提供 tokenizer）");
            System.out.println("- VectorStoreChatMemory: 长对话，需要语义检索");

        } catch (Exception e) {
            System.err.println("错误: " + e.getMessage());
            e.printStackTrace();
        }
    }
}
