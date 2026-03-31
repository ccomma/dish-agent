package com.example.langchain4jdemo.basics;

import com.example.langchain4jdemo.Config;
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
 * 演示如何使用系统消息、用户消息和 LangChain4j 的消息构建模式
 *
 * LangChain4j 0.31.0 中没有独立的 PromptTemplate 类，
 * 而是通过 Message 类（SystemMessage、UserMessage）来构建提示
 */
public class PromptTemplateExample {

    // 简单的模板替换方法（模拟 PromptTemplate 功能）
    static String fillTemplate(String template, String... keyValues) {
        String result = template;
        for (int i = 0; i < keyValues.length - 1; i += 2) {
            result = result.replace("{{" + keyValues[i] + "}}", keyValues[i + 1]);
        }
        return result;
    }

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

        System.out.println("=== 提示模板和系统消息示例 ===\n");

        // 示例1: 使用系统消息定义AI角色
        System.out.println("╔════════════════════════════════════════════════════╗");
        System.out.println("║ 示例1: 使用系统消息定义AI角色                    ║");
        System.out.println("╚════════════════════════════════════════════════════╝\n");

        String systemMessageText = "你是一个有帮助的Java编程助手。你专门帮助开发人员解决Java相关的问题，"
                + "并提供代码示例和最佳实践。你的回答应该简洁明了。";

        String userMessageText = "请解释Java中的多态性，并给出一个简单的示例。";

        // 构建消息列表
        List<ChatMessage> messages = Arrays.asList(
                SystemMessage.from(systemMessageText),
                UserMessage.from(userMessageText)
        );

        Response<AiMessage> response = model.generate(messages);
        System.out.println("系统消息: " + systemMessageText);
        System.out.println("用户消息: " + userMessageText);
        System.out.println("AI响应: " + response.content().text());
        System.out.println("Tokens使用: " + response.tokenUsage());

        System.out.println("\n══════════════════════════════════════════════════════════\n");

        // 示例2: 使用模板替换模式构建提示
        System.out.println("╔════════════════════════════════════════════════════╗");
        System.out.println("║ 示例2: 模板替换模式构建提示                      ║");
        System.out.println("╚════════════════════════════════════════════════════╝\n");

        // 定义模板字符串
        String template = "请将以下英语文本翻译成{{targetLanguage}}：\n{{text}}";

        // 使用模板替换变量
        String filledPrompt = fillTemplate(template,
            "targetLanguage", "中文",
            "text", "Hello, welcome to LangChain4j tutorial. This framework helps you build AI-powered applications in Java."
        );

        System.out.println("模板: " + template);
        System.out.println("\n填充后的 Prompt:");
        System.out.println(filledPrompt);

        // 发送翻译请求
        String translation = model.generate(filledPrompt);
        System.out.println("\n翻译结果: " + translation);

        System.out.println("\n══════════════════════════════════════════════════════════\n");

        // 示例3: 对话历史
        System.out.println("╔════════════════════════════════════════════════════╗");
        System.out.println("║ 示例3: 对话历史示例                              ║");
        System.out.println("╚════════════════════════════════════════════════════╝\n");

        List<ChatMessage> conversation = Arrays.asList(
                SystemMessage.from("你是一个友好的聊天机器人。"),
                UserMessage.from("你好！"),
                AiMessage.from("你好！我是AI助手，有什么可以帮你的吗？"),
                UserMessage.from("你能告诉我今天的天气如何吗？")
        );

        Response<AiMessage> conversationResponse = model.generate(conversation);
        System.out.println("对话历史:");
        conversation.forEach(msg -> System.out.println("  " + msg.type() + ": " + msg.text()));
        System.out.println("\n最新响应: " + conversationResponse.content().text());

        System.out.println("\n══════════════════════════════════════════════════════════\n");

        // 示例4: 复杂模板（多个变量和条件）
        System.out.println("╔════════════════════════════════════════════════════╗");
        System.out.println("║ 示例4: 复杂模板示例                              ║");
        System.out.println("╚════════════════════════════════════════════════════╝\n");

        String complexTemplate =
            "你是一个{{role}}。\n" +
            "用户信息：{{userName}}\n" +
            "用户等级：{{userLevel}}\n" +
            "问题：{{question}}\n\n" +
            "请根据用户等级调整回答的详细程度。";

        String filledComplex = fillTemplate(complexTemplate,
            "role", "技术顾问",
            "userName", "张三",
            "userLevel", "初级",
            "question", "什么是设计模式？"
        );

        System.out.println("模板变量:");
        System.out.println("  role = 技术顾问");
        System.out.println("  userName = 张三");
        System.out.println("  userLevel = 初级");
        System.out.println("  question = 什么是设计模式？");
        System.out.println("\n生成的 Prompt:");
        System.out.println(filledComplex);

        Response<AiMessage> complexResponse = model.generate(
            List.of(SystemMessage.from("你是一个技术顾问"), UserMessage.from(filledComplex))
        );
        System.out.println("\nAI响应: " + complexResponse.content().text());

        System.out.println("\n══════════════════════════════════════════════════════════\n");

        // 示例5: Few-shot 提示模式
        System.out.println("╔════════════════════════════════════════════════════╗");
        System.out.println("║ 示例5: Few-shot 提示示例                        ║");
        System.out.println("╚════════════════════════════════════════════════════╝\n");

        String fewShotPrompt =
            "你是一个情感分析助手。请判断文本的情感是正面(positive)还是负面(negative)。\n\n" +
            "示例：\n" +
            "输入：我今天很开心！ → 输出：positive\n" +
            "输入：这个产品太差了，很失望。 → 输出：negative\n\n" +
            "现在请判断：\n" +
            "输入：{{text}}";

        String inputText = "这家餐厅的食物非常美味，服务也很棒！";
        String filledFewShot = fillTemplate(fewShotPrompt, "text", inputText);

        System.out.println("输入文本: " + inputText);
        System.out.println("\n生成的 Few-shot Prompt:");
        System.out.println(filledFewShot);

        String sentiment = model.generate(filledFewShot);
        System.out.println("\n情感判断: " + sentiment);

        System.out.println("\n══════════════════════════════════════════════════════════\n");

        // 示例6: 多语言翻译模板
        System.out.println("╔════════════════════════════════════════════════════╗");
        System.out.println("║ 示例6: 多语言翻译模板                            ║");
        System.out.println("╚════════════════════════════════════════════════════╝\n");

        String[] languages = {"中文", "日语", "韩语", "法语"};
        String originalText = "LangChain4j makes AI development in Java easy!";

        System.out.println("原文: " + originalText + "\n");

        String current = originalText;
        for (int i = 0; i < languages.length; i++) {
            String lang = languages[i];
            String translatePrompt = "将以下文本翻译成" + lang + "，只返回翻译结果：\n" + current;
            current = model.generate(translatePrompt);
            System.out.println("翻译成" + lang + ": " + current);
        }

        System.out.println("\n最终结果（法语）: " + current);
    }
}
