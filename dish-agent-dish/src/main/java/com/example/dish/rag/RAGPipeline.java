package com.example.dish.rag;

import com.example.dish.service.EmbeddingService;
import dev.langchain4j.data.embedding.Embedding;
import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.model.input.PromptTemplate;
import dev.langchain4j.model.output.Response;
import dev.langchain4j.model.scoring.ScoringModel;
import dev.langchain4j.store.embedding.EmbeddingMatch;
import dev.langchain4j.store.embedding.EmbeddingSearchRequest;
import dev.langchain4j.store.embedding.EmbeddingSearchResult;
import dev.langchain4j.store.embedding.EmbeddingStore;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.Resource;
import org.springframework.core.io.support.PathMatchingResourcePatternResolver;
import org.springframework.stereotype.Component;

import javax.annotation.PostConstruct;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * RAG 管道：向量检索 + 可选重排 + LLM 生成。
 */
@Component
public class RAGPipeline {

    private static final Logger log = LoggerFactory.getLogger(RAGPipeline.class);

    @Autowired
    private EmbeddingService embeddingService;
    @Autowired
    private EmbeddingStore<TextSegment> embeddingStore;
    @Autowired(required = false)
    private ScoringModel scoringModel;
    @Autowired
    private ChatModel chatModel;

    @Value("${rag.knowledge.pattern:classpath*:rag/knowledge/*.md}")
    private String knowledgePattern;

    @PostConstruct
    public void init() {
        loadKnowledgeBase();
    }

    private void loadKnowledgeBase() {
        PathMatchingResourcePatternResolver resolver = new PathMatchingResourcePatternResolver();
        try {
            Resource[] resources = resolver.getResources(knowledgePattern);
            int loaded = 0;
            for (Resource resource : resources) {
                if (!resource.exists()) {
                    continue;
                }
                String content;
                try (var inputStream = resource.getInputStream()) {
                    content = new String(inputStream.readAllBytes(), StandardCharsets.UTF_8);
                }
                if (content.isBlank()) {
                    continue;
                }
                addToVectorStore(content);
                loaded++;
            }
            log.info("RAG knowledge loaded: {} documents from pattern={}", loaded, knowledgePattern);
        } catch (IOException ex) {
            throw new IllegalStateException("failed to load rag knowledge resources", ex);
        }
    }

    private void addToVectorStore(String content) {
        Embedding embedding = embeddingService.embed(content);
        TextSegment segment = TextSegment.from(content);
        embeddingStore.add(embedding, segment);
    }

    private String retrieve(String query, int topK) {
        Embedding queryEmbedding = embeddingService.embed(query);
        EmbeddingSearchResult<TextSegment> initialResult = embeddingStore.search(
                EmbeddingSearchRequest.builder()
                        .queryEmbedding(queryEmbedding)
                        .maxResults(5)
                        .build()
        );

        List<TextSegment> segments = initialResult.matches().stream()
                .map(EmbeddingMatch::embedded)
                .toList();
        List<TextSegment> finalSegments = rerankSegments(segments, query, topK);

        StringBuilder context = new StringBuilder();
        context.append("【参考信息】\n\n");
        if (finalSegments.isEmpty()) {
            context.append("未找到直接相关的参考信息。\n");
            return context.toString();
        }

        for (TextSegment segment : finalSegments) {
            context.append("─".repeat(40)).append('\n');
            context.append("【").append(extractName(segment.text())).append("】\n");
            context.append(segment.text()).append("\n\n");
        }
        return context.toString();
    }

    private List<TextSegment> rerankSegments(List<TextSegment> segments, String query, int topK) {
        if (segments.isEmpty()) {
            return List.of();
        }
        if (scoringModel == null) {
            return segments.stream().limit(topK).toList();
        }

        Response<List<Double>> scores = scoringModel.scoreAll(segments, query);
        List<Double> scoreList = scores.content();
        List<Map.Entry<Double, TextSegment>> scored = new ArrayList<>();
        for (int i = 0; i < segments.size() && i < scoreList.size(); i++) {
            scored.add(Map.entry(scoreList.get(i), segments.get(i)));
        }
        scored.sort((a, b) -> Double.compare(b.getKey(), a.getKey()));
        return scored.stream().limit(topK).map(Map.Entry::getValue).toList();
    }

    private String extractName(String text) {
        if (text.startsWith("【") && text.contains("】")) {
            return text.substring(1, text.indexOf("】"));
        }
        return "知识片段";
    }

    private static final PromptTemplate ANSWER_TEMPLATE = PromptTemplate.from(
            "你是一个专业的餐饮智能助手。根据提供的参考信息，准确回答用户的问题。\n" +
                    "\n" +
                    "重要规则：\n" +
                    "- 必须基于参考信息回答，不要编造信息\n" +
                    "- 如果参考信息中没有相关内容，请明确告知用户\n" +
                    "- 回答要清晰、专业、易懂\n" +
                    "- 适当使用列表格式，让信息更易读\n" +
                    "\n" +
                    "{{context}}\n" +
                    "【用户问题】\n" +
                    "{{question}}"
    );

    public String answer(String question) {
        String context = retrieve(question, 3);
        String prompt = ANSWER_TEMPLATE.apply(Map.of("context", context, "question", question)).text();
        return chatModel.chat(prompt);
    }
}
