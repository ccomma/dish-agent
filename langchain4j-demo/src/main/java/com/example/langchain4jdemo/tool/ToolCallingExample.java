package com.example.langchain4jdemo.tool;

import com.example.langchain4jdemo.Config;
import dev.langchain4j.agent.tool.Tool;
import dev.langchain4j.memory.chat.MessageWindowChatMemory;
import dev.langchain4j.model.openai.OpenAiChatModel;
import dev.langchain4j.service.AiServices;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.Map;

/**
 * 工具调用和函数调用示例
 * 演示如何让AI模型调用自定义工具（函数）
 */
public class ToolCallingExample {

    // 工具类：包含AI可以调用的方法
    static class CalculatorTools {

        @Tool("计算两个数字的和")
        public double add(double a, double b) {
            System.out.println("[工具调用] 计算 " + a + " + " + b);
            return a + b;
        }

        @Tool("计算两个数字的差")
        public double subtract(double a, double b) {
            System.out.println("[工具调用] 计算 " + a + " - " + b);
            return a - b;
        }

        @Tool("计算两个数字的乘积")
        public double multiply(double a, double b) {
            System.out.println("[工具调用] 计算 " + a + " × " + b);
            return a * b;
        }

        @Tool("计算两个数字的商")
        public double divide(double a, double b) {
            if (b == 0) {
                throw new IllegalArgumentException("除数不能为零");
            }
            System.out.println("[工具调用] 计算 " + a + " ÷ " + b);
            return a / b;
        }

        @Tool("计算数字的平方")
        public double square(double a) {
            System.out.println("[工具调用] 计算 " + a + " 的平方");
            return a * a;
        }
    }

    static class WeatherTools {
        private final Map<String, String> weatherData = new HashMap<>();

        public WeatherTools() {
            // 模拟天气数据
            weatherData.put("北京", "晴朗，25°C，湿度45%");
            weatherData.put("上海", "多云，22°C，湿度60%");
            weatherData.put("广州", "小雨，28°C，湿度75%");
            weatherData.put("深圳", "阵雨，27°C，湿度70%");
            weatherData.put("成都", "阴天，20°C，湿度65%");
        }

        @Tool("获取指定城市的当前天气")
        public String getWeather(String city) {
            System.out.println("[工具调用] 获取 " + city + " 的天气");
            String weather = weatherData.get(city);
            if (weather == null) {
                return "抱歉，没有找到 " + city + " 的天气信息";
            }
            return city + "的天气：" + weather;
        }

        @Tool("获取当前日期和时间")
        public String getCurrentDateTime() {
            System.out.println("[工具调用] 获取当前时间");
            return LocalDateTime.now().format(
                DateTimeFormatter.ofPattern("yyyy年MM月dd日 HH:mm:ss")
            );
        }
    }

    static class DatabaseTools {
        private final Map<String, String> userDatabase = new HashMap<>();

        public DatabaseTools() {
            // 模拟用户数据库
            userDatabase.put("张三", "年龄：30，职位：软件工程师，部门：研发部");
            userDatabase.put("李四", "年龄：28，职位：产品经理，部门：产品部");
            userDatabase.put("王五", "年龄：35，职位：项目经理，部门：项目部");
        }

        @Tool("根据姓名查询用户信息")
        public String getUserInfo(String name) {
            System.out.println("[工具调用] 查询用户 " + name);
            String info = userDatabase.get(name);
            if (info == null) {
                return "未找到用户: " + name;
            }
            return "用户 " + name + " 的信息：" + info;
        }
    }

    // 定义AI助手接口
    interface Assistant {
        String chat(String userMessage);
    }

    public static void main(String[] args) {
        // 从配置文件加载配置
        Config config = Config.getInstance();

        System.out.println("=== 工具调用和函数调用示例 ===");
        System.out.println("本示例演示AI如何调用自定义工具（函数）\n");

        try {
            // 创建工具实例
            CalculatorTools calculator = new CalculatorTools();
            WeatherTools weather = new WeatherTools();
            DatabaseTools database = new DatabaseTools();

            // 创建AI助手，注入所有工具
            Assistant assistant = AiServices.builder(Assistant.class)
                    .chatModel(OpenAiChatModel.builder()
                            .apiKey(config.getApiKey())
                            .baseUrl(config.getBaseUrl())
                            .modelName(config.getModel())
                            .temperature(0.1) // 较低温度以获得更确定的工具调用
                            .build())
                    .chatMemory(MessageWindowChatMemory.withMaxMessages(20))
                    .tools(calculator, weather, database)
                    .build();

            // 测试对话
            String[] testConversations = {
                "计算一下 25 加 38 等于多少？",
                "北京现在的天气怎么样？",
                "告诉我张三的信息",
                "先计算 15 的平方，再加上 20，然后除以 5",
                "现在是什么时间？上海和广州的天气分别怎么样？",
                "查询李四的信息，然后计算他的年龄加上10是多少？"
            };

            for (String question : testConversations) {
                System.out.println("用户: " + question);
                String response = assistant.chat(question);
                System.out.println("AI: " + response);
                System.out.println("---");
                Thread.sleep(1000); // 稍微延迟以便观察
            }

            // 演示连续对话
            System.out.println("\n=== 连续对话演示 ===");

            String[] multiTurnConversation = {
                "计算 12 乘以 8",
                "很好，现在用那个结果减去 15",
                "现在告诉我深圳的天气",
                "最后，计算上面那个结果的平方根是多少？"
            };

            for (String userInput : multiTurnConversation) {
                System.out.println("用户: " + userInput);
                String response = assistant.chat(userInput);
                System.out.println("AI: " + response);
                System.out.println("---");
                Thread.sleep(1000);
            }

            // 演示工具调用错误处理
            System.out.println("\n=== 错误处理演示 ===");
            System.out.println("用户: 用零除以五是多少？");
            try {
                String response = assistant.chat("用零除以五是多少？");
                System.out.println("AI: " + response);
            } catch (Exception e) {
                System.out.println("AI: 计算时出现错误: " + e.getMessage());
            }

            System.out.println("\n用户: 查询一个不存在的用户");
            String response = assistant.chat("查询用户赵六的信息");
            System.out.println("AI: " + response);

        } catch (Exception e) {
            System.err.println("错误: " + e.getMessage());
            e.printStackTrace();
        }
    }
}