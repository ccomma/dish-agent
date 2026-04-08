package com.enterprise.langchain4j.rag;

import com.enterprise.langchain4j.Config;
import dev.langchain4j.data.document.Document;
import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.model.openai.OpenAiChatModel;

import java.io.InputStream;
import java.util.*;

/**
 * RAG 管道（简化版）
 *
 * 注意：由于 langchain4j-transformers 在某些版本中不可用，
 * 此实现使用关键词匹配进行检索作为演示。
 *
 * 在生产环境中，可以：
 * 1. 使用 OpenAI 的 text-embedding-3-small 或 text-embedding-3-large
 * 2. 部署独立的 Embedding 服务
 * 3. 使用云提供的向量数据库（如 Milvus + OpenAI Embedding）
 */
public class RAGPipeline {

    private final ChatModel chatModel;
    private final Map<String, KnowledgeEntry> knowledgeBase;

    /**
     * 知识条目
     */
    private static class KnowledgeEntry {
        String type;      // dish, policy
        String name;      // 名称
        String content;   // 完整内容
        Set<String> keywords; // 关键词

        KnowledgeEntry(String type, String name, String content) {
            this.type = type;
            this.name = name;
            this.content = content;
            this.keywords = extractKeywords(content);
        }

        Set<String> extractKeywords(String text) {
            Set<String> keywords = new HashSet<>();
            // 简单分词
            String[] words = text.split("[，、。\\s（）()、]");
            for (String word : words) {
                if (word.length() >= 2) {
                    keywords.add(word.toLowerCase());
                }
            }
            return keywords;
        }
    }

    public RAGPipeline() {
        Config config = Config.getInstance();

        // 初始化聊天模型
        this.chatModel = OpenAiChatModel.builder()
                .apiKey(config.getApiKey())
                .baseUrl(config.getBaseUrl())
                .modelName(config.getModel())
                .temperature(0.3)
                .build();

        // 知识库
        this.knowledgeBase = new LinkedHashMap<>();

        // 加载知识库
        loadKnowledgeBase();
    }

    /**
     * 加载知识库
     */
    private void loadKnowledgeBase() {
        System.out.println("[RAG] 加载知识库...");

        // 加载菜品
        loadDishes();

        // 加载政策
        loadPolicies();

        System.out.println("[RAG] 知识库加载完成，共 " + knowledgeBase.size() + " 个条目");
    }

    private void loadDishes() {
        // 宫保鸡丁
        String gongbao = """
            【宫保鸡丁】

            分类：川菜
            辣度：2级（微辣）
            价格：38元

            成分：
            - 主料：鸡胸肉200g
            - 配料：花生米50g、干辣椒10g、花椒5g、葱段20g、姜片10g、蒜片10g

            过敏原：花生、大豆

            营养信息（每100g）：
            - 热量：215kcal
            - 蛋白质：26g
            - 脂肪：11g
            - 碳水化合物：5g

            做法步骤：
            1. 鸡胸肉切丁，加入盐、料酒、淀粉腌制15分钟
            2. 调制宫保汁：生抽2勺、醋1勺、糖1勺、淀粉半勺、水2勺
            3. 热油至六成，下花生米小火炸至金黄酥脆，捞出备用
            4. 锅留底油，小火煸香干辣椒和花椒
            5. 下鸡丁大火快炒至变色
            6. 淋入宫保汁快速翻炒均匀
            7. 关火后加入炸好的花生米拌匀即可

            烹饪技巧：
            - 鸡丁腌制时加少许蛋清可使肉质更嫩
            - 花生米最后放才能保持酥脆口感
            - 火候控制很重要，鸡丁变色即可不要炒老

            适用场景：下饭、朋友聚餐、川菜爱好者、家常菜
            配酒建议：可乐、啤酒、酸梅汤
            """;
        knowledgeBase.put("宫保鸡丁", new KnowledgeEntry("dish", "宫保鸡丁", gongbao));

        // 麻婆豆腐
        String mapo = """
            【麻婆豆腐】

            分类：川菜
            辣度：3级（中辣）
            价格：28元

            成分：
            - 主料：嫩豆腐300g
            - 配料：牛肉末80g、豆瓣酱15g、豆豉5g、花椒粉3g、辣椒粉5g、蒜末10g、姜末5g、葱花10g

            过敏原：大豆、小麦

            营养信息（每100g）：
            - 热量：185kcal
            - 蛋白质：12g
            - 脂肪：13g
            - 碳水化合物：4g

            做法步骤：
            1. 豆腐切2cm见方小块，放入淡盐水中浸泡10分钟去豆腥
            2. 锅中放油，小火炒香肉末至变色出香
            3. 加入豆瓣酱、豆豉小火煸出红油
            4. 加入蒜末、姜末、辣椒粉炒香
            5. 加入适量清水或高汤烧开
            6. 轻轻放入豆腐块，中小火烧5分钟入味
            7. 加少许生抽调味，水淀粉勾芡
            8. 出锅撒花椒粉和葱花即可

            烹饪技巧：
            - 豆腐用盐水浸泡可以使其更加紧实不易碎
            - 炒豆瓣酱时火候要小，避免炒糊
            - 勾芡要分两次，使汤汁更浓稠
            - 花椒粉一定要出锅前撒，麻味更突出

            适用场景：下饭、家常菜、川菜爱好者、素食者适宜(可去肉末)
            配酒建议：米饭、啤酒、凉茶
            """;
        knowledgeBase.put("麻婆豆腐", new KnowledgeEntry("dish", "麻婆豆腐", mapo));

        System.out.println("[RAG] 加载菜品: 宫保鸡丁, 麻婆豆腐");
    }

    private void loadPolicies() {
        String refundPolicy = """
            【退款与售后规则】

            一、退菜规则

            1. 菜品上桌前
               - 可全额退款，无需任何手续费
               - 直接联系服务员或通过APP操作即可

            2. 菜品上桌后30分钟内
               - 如对菜品不满意（如味道过咸、过辣等），可申请换菜
               - 换菜差价多退少补

            3. 菜品上桌后超过30分钟
               - 原则上不接受退菜（特殊情况除外）
               - 如菜品存在质量问题（异物、变质等），可随时退菜

            4. 以下情况可随时退菜
               - 菜品内有异物（虫子、头发、玻璃等）
               - 菜品变质或有异味
               - 菜品严重少料或做错订单

            二、退款规则

            1. 退款时限
               - 退款申请审核通过后，3-7个工作日内原路返回
               - 如使用现金支付，退款至用户账户余额

            2. 退款金额计算
               - 上桌前退菜：全额退款
               - 上桌后退菜：根据实际情况，与门店协商

            3. 以下情况可优先退款
               - 门店原因导致的订单错误
               - 长时间未送达（超过承诺时间30分钟）
               - 菜品质量严重问题

            三、投诉与售后

            1. 客服渠道
               - 热线电话：400-888-9999
               - 在线客服：APP内右下角"帮助与客服"
               - 工作时间：9:00-22:00

            2. 投诉处理时效
               - 紧急投诉：2小时内响应
               - 一般投诉：24小时内处理
               - 投诉结果会有短信通知

            四、优惠券与补偿

            1. 因门店原因导致的退款
               - 自动赠送优惠券（退款金额的10%）
               - 优惠券有效期30天

            2. 会员专属权益
               - 银卡会员：额外获得5%退款补偿
               - 金卡会员：额外获得10%退款补偿
               - 钻石会员：额外获得15%退款补偿+专属客服通道
            """;
        knowledgeBase.put("退款规则", new KnowledgeEntry("policy", "退款规则", refundPolicy));

        System.out.println("[RAG] 加载政策文档: 退款规则");
    }

    /**
     * 检索相关知识
     * 使用关键词匹配 + 评分算法
     */
    private String retrieve(String query, int topK) {
        String[] queryWords = query.toLowerCase().split("[\\s，。？?！!、，]");
        Set<String> queryKeywords = new HashSet<>();
        for (String word : queryWords) {
            if (word.length() >= 2) {
                queryKeywords.add(word);
            }
        }

        // 计算每个条目的相关性分数
        List<Map.Entry<String, KnowledgeEntry>> scored = new ArrayList<>();
        for (Map.Entry<String, KnowledgeEntry> entry : knowledgeBase.entrySet()) {
            KnowledgeEntry ke = entry.getValue();
            int matchCount = 0;
            for (String keyword : queryKeywords) {
                if (ke.keywords.contains(keyword) || ke.content.toLowerCase().contains(keyword)) {
                    matchCount++;
                }
            }
            if (matchCount > 0) {
                scored.add(entry);
            }
        }

        // 按匹配度排序
        scored.sort((a, b) -> {
            int scoreA = countMatches(a.getValue(), queryKeywords);
            int scoreB = countMatches(b.getValue(), queryKeywords);
            return Integer.compare(scoreB, scoreA);
        });

        // 构建上下文
        StringBuilder context = new StringBuilder();
        context.append("【参考信息】\n\n");

        int count = 0;
        for (Map.Entry<String, KnowledgeEntry> entry : scored) {
            if (count >= topK) break;
            KnowledgeEntry ke = entry.getValue();
            context.append("─".repeat(40)).append("\n");
            context.append("【").append(ke.name).append("】(").append(ke.type).append(")\n");
            context.append(ke.content).append("\n\n");
            count++;
        }

        if (count == 0) {
            context.append("未找到直接相关的参考信息。\n");
        }

        return context.toString();
    }

    private int countMatches(KnowledgeEntry ke, Set<String> queryKeywords) {
        int count = 0;
        String contentLower = ke.content.toLowerCase();
        for (String keyword : queryKeywords) {
            if (contentLower.contains(keyword)) {
                count++;
            }
        }
        return count;
    }

    /**
     * 使用 RAG 回答问题
     */
    public String answer(String question) {
        // 1. 检索相关文档
        String context = retrieve(question, 3);

        // 2. 构建提示词
        String prompt = String.format("""
            你是一个专业的餐饮智能助手。根据提供的参考信息，准确回答用户的问题。

            重要规则：
            - 必须基于参考信息回答，不要编造信息
            - 如果参考信息中没有相关内容，请明确告知用户
            - 回答要清晰，专业、易懂
            - 适当使用列表格式，让信息更易读

            %s

            【用户问题】
            %s
            """, context, question);

        // 3. 调用 LLM 生成
        return chatModel.chat(prompt);
    }

    /**
     * 演示 RAG 问答
     */
    public static void main(String[] args) {
        System.out.println("=== RAG 管道演示 ===\n");

        RAGPipeline rag = new RAGPipeline();

        // 测试问题
        String[] questions = {
            "宫保鸡丁用什么肉？",
            "宫保鸡丁怎么做？",
            "麻婆豆腐辣不辣？",
            "怎么申请退款？",
            "退款要多久到账？",
            "你们有什么菜？"
        };

        for (String question : questions) {
            System.out.println("【问题】" + question);
            System.out.println();
            String answer = rag.answer(question);
            System.out.println("【回答】" + answer);
            System.out.println();
            System.out.println("─".repeat(50));
            System.out.println();
        }
    }
}
