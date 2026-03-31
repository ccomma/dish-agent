package com.example.langchain4jdemo.output;

import com.example.langchain4jdemo.Config;
import dev.langchain4j.model.chat.ChatLanguageModel;
import dev.langchain4j.model.openai.OpenAiChatModel;
import dev.langchain4j.service.AiServices;
import dev.langchain4j.service.SystemMessage;

import java.util.List;

/**
 * 输出解析器（Output Parser）示例
 * 演示如何将 LLM 的文本输出解析为结构化的 Java 对象
 */
public class OutputParserExample {

    // ═══════════════════════════════════════════════════════════════════
    // 示例1: 手动 JSON 解析
    // ═══════════════════════════════════════════════════════════════════
    static void example1ManualJsonParsing(Config config) {
        System.out.println("╔════════════════════════════════════════════════════╗");
        System.out.println("║ 示例1: 手动 JSON 解析                            ║");
        System.out.println("╚════════════════════════════════════════════════════╝\n");

        ChatLanguageModel model = OpenAiChatModel.builder()
                .apiKey(config.getApiKey())
                .baseUrl(config.getBaseUrl())
                .modelName(config.getModel())
                .temperature(0.3)
                .build();

        String prompt = """
            请以 JSON 格式返回一个人的信息，包含以下字段：
            - name: 姓名
            - age: 年龄
            - email: 邮箱
            只返回 JSON，不要其他文字。
            """;

        String jsonResponse = model.generate(prompt);
        System.out.println("原始响应:\n" + jsonResponse);

        // 手动解析（实际项目中建议使用 Jackson 或 Gson）
        String name = extractJsonField(jsonResponse, "name");
        String age = extractJsonField(jsonResponse, "age");
        String email = extractJsonField(jsonResponse, "email");

        System.out.println("\n解析结果:");
        System.out.println("  姓名: " + name);
        System.out.println("  年龄: " + age);
        System.out.println("  邮箱: " + email);
    }

    static String extractJsonField(String json, String field) {
        try {
            int fieldStart = json.indexOf("\"" + field + "\"");
            if (fieldStart == -1) {
                return "未知";
            }
            int colonPos = json.indexOf(":", fieldStart);
            int valueStart = json.indexOf("\"", colonPos) + 1;
            int valueEnd = json.indexOf("\"", valueStart);
            return json.substring(valueStart, valueEnd);
        } catch (Exception e) {
            return "解析失败";
        }
    }

    // ═══════════════════════════════════════════════════════════════════
    // 示例2: 使用 AiServices 自动映射
    // ═══════════════════════════════════════════════════════════════════
    static void example2AiServicesMapping(Config config) {
        System.out.println("\n══════════════════════════════════════════════════════════\n");
        System.out.println("╔════════════════════════════════════════════════════╗");
        System.out.println("║ 示例2: AiServices 自动映射                        ║");
        System.out.println("╚════════════════════════════════════════════════════╝\n");

        ChatLanguageModel model = OpenAiChatModel.builder()
                .apiKey(config.getApiKey())
                .baseUrl(config.getBaseUrl())
                .modelName(config.getModel())
                .temperature(0.3)
                .build();

        // 定义接口，LLM 输出会自动映射到 PersonInfo
        interface PersonExtractor {
            @SystemMessage("你是一个信息提取助手，从文本中提取人员信息。返回 JSON 格式：{\"name\":\"...\",\"age\":...}")
            String extract(String text);
        }

        PersonExtractor extractor = AiServices.builder(PersonExtractor.class)
                .chatLanguageModel(model)
                .build();

        String text = "张三是一位35岁的软件工程师，他喜欢编程、阅读和徒步旅行。他在一家科技公司工作。";

        System.out.println("输入文本: " + text);
        String jsonResult = extractor.extract(text);
        System.out.println("LLM 返回: " + jsonResult);

        // 手动解析
        System.out.println("\n解析结果:");
        System.out.println("  姓名: " + extractJsonField(jsonResult, "name"));
        System.out.println("  年龄: " + extractJsonField(jsonResult, "age"));
    }

    // ═══════════════════════════════════════════════════════════════════
    // 示例3: 列表数据解析
    // ═══════════════════════════════════════════════════════════════════
    static void example3ListParsing(Config config) {
        System.out.println("\n══════════════════════════════════════════════════════════\n");
        System.out.println("╔════════════════════════════════════════════════════╗");
        System.out.println("║ 示例3: 列表数据解析                            ║");
        System.out.println("╚════════════════════════════════════════════════════╝\n");

        ChatLanguageModel model = OpenAiChatModel.builder()
                .apiKey(config.getApiKey())
                .baseUrl(config.getBaseUrl())
                .modelName(config.getModel())
                .temperature(0.3)
                .build();

        interface ListExtractor {
            @SystemMessage("你是一个信息提取助手。从文本中提取所有技术术语，返回 JSON 数组格式：\n" +
                    "例如：{\"terms\":[{\"term\":\"名称\",\"description\":\"描述\"},...]}")
            String extract(String text);
        }

        ListExtractor extractor = AiServices.builder(ListExtractor.class)
                .chatLanguageModel(model)
                .build();

        String text = """
            本系统使用了多种技术：
            1. LangChain4j - Java 的 LLM 开发框架
            2. Spring Boot - 流行的微服务框架
            3. Redis - 高性能缓存数据库
            """;

        System.out.println("输入文本:\n" + text);
        String jsonResult = extractor.extract(text);
        System.out.println("\nLLM 返回:\n" + jsonResult);
    }

    // ═══════════════════════════════════════════════════════════════════
    // 示例4: 枚举解析
    // ═══════════════════════════════════════════════════════════════════
    static void example4EnumParsing(Config config) {
        System.out.println("\n══════════════════════════════════════════════════════════\n");
        System.out.println("╔════════════════════════════════════════════════════╗");
        System.out.println("║ 示例4: 枚举解析                                ║");
        System.out.println("╚════════════════════════════════════════════════════╝\n");

        ChatLanguageModel model = OpenAiChatModel.builder()
                .apiKey(config.getApiKey())
                .baseUrl(config.getBaseUrl())
                .modelName(config.getModel())
                .temperature(0.3)
                .build();

        interface SentimentAnalyzer {
            @SystemMessage("分析用户评论的情感倾向。返回 JSON 格式：{\"sentiment\":\"POSITIVE|NEGATIVE|NEUTRAL\"}")
            String analyze(String review);
        }

        SentimentAnalyzer analyzer = AiServices.builder(SentimentAnalyzer.class)
                .chatLanguageModel(model)
                .build();

        String[] reviews = {
            "这个产品太棒了！完全超出预期！",
            "质量很差，用了两天就坏了",
            "还行吧，中规中矩"
        };

        for (String review : reviews) {
            System.out.println("评论: " + review);
            try {
                String result = analyzer.analyze(review);
                System.out.println("结果: " + result);
            } catch (Exception e) {
                System.out.println("解析失败: " + e.getMessage());
            }
            System.out.println();
        }
    }

    // ═══════════════════════════════════════════════════════════════════
    // 示例5: 多字段复杂对象
    // ═══════════════════════════════════════════════════════════════════
    static void example5ComplexObject(Config config) {
        System.out.println("\n══════════════════════════════════════════════════════════\n");
        System.out.println("╔════════════════════════════════════════════════════╗");
        System.out.println("║ 示例5: 多字段复杂对象解析                        ║");
        System.out.println("╚════════════════════════════════════════════════════╝\n");

        ChatLanguageModel model = OpenAiChatModel.builder()
                .apiKey(config.getApiKey())
                .baseUrl(config.getBaseUrl())
                .modelName(config.getModel())
                .temperature(0.3)
                .build();

        interface OrderExtractor {
            @SystemMessage("你是一个订单信息提取助手。从文本中提取订单详情，返回 JSON 格式：\n" +
                    "{\"orderId\":\"...\",\"customerName\":\"...\",\"items\":\"...\",\"totalAmount\":...,\"status\":\"...\"}")
            String extract(String text);
        }

        OrderExtractor extractor = AiServices.builder(OrderExtractor.class)
                .chatLanguageModel(model)
                .build();

        String orderText = """
            订单编号：ORD-2024-00123
            客户姓名：张三
            商品清单：iPhone 15 Pro x1, AirPods Pro x2
            订单金额：9299.00
            订单状态：已发货
            """;

        System.out.println("输入文本:\n" + orderText);
        try {
            String jsonResult = extractor.extract(orderText);
            System.out.println("\nLLM 返回:\n" + jsonResult);
        } catch (Exception e) {
            System.out.println("解析失败: " + e.getMessage());
        }
    }

    public static void main(String[] args) {
        Config config = Config.getInstance();

        System.out.println("=== LangChain4j 输出解析器示例 ===\n");

        try {
            example1ManualJsonParsing(config);
            example2AiServicesMapping(config);
            example3ListParsing(config);
            example4EnumParsing(config);
            example5ComplexObject(config);

            System.out.println("\n══════════════════════════════════════════════════════════\n");
            System.out.println("示例完成！");
            System.out.println("\n【输出解析方式总结】");
            System.out.println("1. 手动解析：适合简单场景，使用字符串处理");
            System.out.println("2. AiServices 自动映射：推荐方式，通过接口定义自动映射");
            System.out.println("3. JSON 库：Jackson/Gson，适合复杂 JSON 结构");
            System.out.println("\n注意：自动映射依赖模型输出符合预期格式，建议在 prompt 中明确要求 JSON 格式。");

        } catch (Exception e) {
            System.err.println("错误: " + e.getMessage());
            e.printStackTrace();
        }
    }
}
