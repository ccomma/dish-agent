package com.example.langchain4jdemo.output;

import com.example.langchain4jdemo.Config;
import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.model.openai.OpenAiChatModel;
import dev.langchain4j.service.AiServices;
import dev.langchain4j.service.SystemMessage;
import dev.langchain4j.service.UserMessage;

import java.util.List;

/**
 * 结构化输出示例
 * 演示如何使用 LangChain4j AiServices 将 AI 响应直接映射到 Java 对象
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

    // 定义产品数据结构
    public static class Product {
        public String name;
        public String description;
        public double price;
        public int stock;
        public String category;

        @Override
        public String toString() {
            return String.format("产品名: %s, 描述: %s, 价格: %.2f, 库存: %d, 分类: %s",
                    name, description, price, stock, category);
        }
    }

    // 天气预报数据结构
    public static class Weather {
        public String city;
        public String date;
        public String condition;
        public int highTemp;
        public int lowTemp;
        public int humidity;
        public int windSpeed;

        @Override
        public String toString() {
            return String.format("城市: %s, 日期: %s, 天气: %s, 温度: %d-%d°C, 湿度: %d%%, 风速: %dkm/h",
                    city, date, condition, lowTemp, highTemp, humidity, windSpeed);
        }
    }

    // AI 助手接口 - 使用 AiServices 直接映射到 POJO
    // 注意：接口必须是 static 才能被外部直接引用
    static interface PersonExtractor {
        @SystemMessage("你是一个信息提取助手。从文本中提取人员信息，返回JSON格式包含：name(姓名)、age(年龄)、occupation(职业)、hobbies(兴趣爱好列表）。")
        Person extract(@UserMessage String text);
    }

    static interface ProductGenerator {
        @SystemMessage("你是一个产品信息助手。生成产品信息，返回JSON格式包含：name(产品名)、description(描述)、price(价格)、stock(库存)、category(分类)。")
        Product generate(@UserMessage String request);
    }

    static interface WeatherExtractor {
        @SystemMessage("你是一个天气预报信息提取助手。从文本中提取天气信息，返回JSON格式包含：city(城市)、date(日期)、condition(天气状况)、highTemp(最高温度)、lowTemp(最低温度)、humidity(湿度)、windSpeed(风速)。")
        Weather extract(@UserMessage String text);
    }

    public static void main(String[] args) {
        // 从配置文件加载配置
        Config config = Config.getInstance();

        System.out.println("=== 结构化输出示例 ===");
        System.out.println("演示如何使用 AiServices 将 AI 响应直接映射到 Java 对象\n");

        try {
            // 创建聊天模型
            ChatModel model = OpenAiChatModel.builder()
                    .apiKey(config.getApiKey())
                    .baseUrl(config.getBaseUrl())
                    .modelName(config.getModel())
                    .temperature(0.3)
                    .build();

            // 创建 AiServices 实例
            PersonExtractor personExtractor = AiServices.create(PersonExtractor.class, model);

            ProductGenerator productGenerator = AiServices.create(ProductGenerator.class, model);

            WeatherExtractor weatherExtractor = AiServices.create(WeatherExtractor.class, model);

            // 示例1: 从文本中提取人物信息（直接映射到 Person 对象）
            System.out.println("╔════════════════════════════════════════════════════╗");
            System.out.println("║ 示例1: 从文本中提取人物信息（POJO 映射）        ║");
            System.out.println("╚════════════════════════════════════════════════════╝\n");

            String text1 = "张三是一位35岁的软件工程师，他喜欢编程、阅读和徒步旅行。"
                    + "他在一家科技公司工作，专门从事人工智能开发。";

            System.out.println("原始文本: " + text1);
            Person person = personExtractor.extract(text1);
            System.out.println("提取结果（Person 对象）: " + person);
            System.out.println();

            // 示例2: 生成产品信息（直接映射到 Product 对象）
            System.out.println("╔════════════════════════════════════════════════════╗");
            System.out.println("║ 示例2: 生成产品信息（POJO 映射）                ║");
            System.out.println("╚════════════════════════════════════════════════════╝\n");

            String productRequest = "生成一个智能手机的产品信息";
            System.out.println("请求: " + productRequest);
            Product product = productGenerator.generate(productRequest);
            System.out.println("生成结果（Product 对象）: " + product);
            System.out.println();

            // 示例3: 从天气预报文本中提取数据（直接映射到 Weather 对象）
            System.out.println("╔════════════════════════════════════════════════════╗");
            System.out.println("║ 示例3: 天气预报提取（POJO 映射）                ║");
            System.out.println("╚════════════════════════════════════════════════════╝\n");

            String weatherText = "北京明天（2024-06-15）的天气预报："
                    + "晴转多云，温度18到25摄氏度，湿度60%，风速10公里/小时。";

            System.out.println("原始文本: " + weatherText);
            Weather weather = weatherExtractor.extract(weatherText);
            System.out.println("提取结果（Weather 对象）: " + weather);

        } catch (Exception e) {
            System.err.println("错误: " + e.getMessage());
            e.printStackTrace();
        }
    }
}
