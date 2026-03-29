package com.example.langchain4jdemo;

import dev.langchain4j.model.chat.ChatLanguageModel;
import dev.langchain4j.model.openai.OpenAiChatModel;

import java.time.LocalDate;
import java.util.List;

/**
 * 结构化输出示例
 * 演示如何从AI模型获取结构化的JSON输出
 */
public class StructuredOutputExample {

    // 定义结构化输出的数据结构

    public static class Person {
        public String name;
        public int age;
        public String occupation;
        public List<String> hobbies;

        @Override
        public String toString() {
            return String.format("姓名: %s, 年龄: %d, 职业: %s, 兴趣爱好: %s",
                    name, age, occupation, hobbies);
        }
    }

    public static void main(String[] args) {
        // 从配置文件加载配置
        Config config = Config.getInstance();

        System.out.println("=== 结构化输出示例 ===");
        System.out.println("演示如何从AI模型获取结构化的JSON数据\n");

        try {
            // 创建Minimax聊天模型
            ChatLanguageModel model = OpenAiChatModel.builder()
                    .apiKey(config.getApiKey())
                    .baseUrl(config.getBaseUrl())
                    .modelName(config.getModel())
                    .temperature(0.3)
                    .build();

            // 示例1: 从文本中提取人物信息
            System.out.println("1. 从文本中提取人物信息:");

            String text1 = "张三是一位35岁的软件工程师，他喜欢编程、阅读和徒步旅行。"
                    + "他在一家科技公司工作，专门从事人工智能开发。";

            String prompt = "从以下文本中提取人物信息，以JSON格式返回，包含name、age、occupation、hobbies字段。\n文本：" + text1;

            String jsonResponse = model.generate(prompt);

            System.out.println("原始文本: " + text1);
            System.out.println("提取的结构化数据:");
            System.out.println(jsonResponse);
            System.out.println();

            // 示例2: 生成产品信息
            System.out.println("2. 生成产品信息:");

            String productPrompt = "生成一个智能手机的产品信息，以JSON格式返回，包含name、description、price、stock、category字段。";

            String productResponse = model.generate(productPrompt);

            System.out.println("生成的产品信息:");
            System.out.println(productResponse);
            System.out.println();

            // 示例3: 从天气预报文本中提取结构化数据
            System.out.println("3. 从天气预报中提取结构化数据:");

            String weatherText = "北京明天（2024-06-15）的天气预报："
                    + "晴转多云，温度18到25摄氏度，湿度60%，风速10公里/小时。";

            String weatherPrompt = "从以下天气预报文本中提取信息，以JSON格式返回，包含city、temperature、condition、humidity、windSpeed、date字段。\n文本：" + weatherText;

            String weatherResponse = model.generate(weatherPrompt);

            System.out.println("原始天气预报: " + weatherText);
            System.out.println("提取的结构化数据:");
            System.out.println(weatherResponse);
            System.out.println();

            // 示例4: 复杂结构
            System.out.println("4. 从新闻中提取多个实体:");

            String news = "在最近的科技大会上，苹果公司发布了新款iPhone。"
                    + "CEO蒂姆·库克表示，新设备采用了更先进的AI芯片。"
                    + "同时，谷歌也宣布了其最新的AI助手功能。"
                    + "微软则展示了与OpenAI合作的新成果。";

            String newsPrompt = "从以下新闻中提取公司信息，以JSON格式返回，包含companies数组，每个公司有name、ceo、announcement、product字段。同时提取technologies数组和summary。\n新闻：" + news;

            String newsResponse = model.generate(newsPrompt);

            System.out.println("原始新闻: " + news);
            System.out.println("提取的结构化数据:");
            System.out.println(newsResponse);

        } catch (Exception e) {
            System.err.println("错误: " + e.getMessage());
            e.printStackTrace();
        }
    }
}