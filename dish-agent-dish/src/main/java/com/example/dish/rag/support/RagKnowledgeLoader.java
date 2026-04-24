package com.example.dish.rag.support;

import com.example.dish.service.EmbeddingService;
import dev.langchain4j.data.embedding.Embedding;
import dev.langchain4j.data.segment.TextSegment;
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

/**
 * RAG 知识预热器。
 * 负责在服务启动时扫描本地知识文件并写入 embedding store，避免主管道类同时承担启动初始化职责。
 */
@Component
public class RagKnowledgeLoader {

    private static final Logger log = LoggerFactory.getLogger(RagKnowledgeLoader.class);

    @Autowired
    private EmbeddingService embeddingService;
    @Autowired
    private EmbeddingStore<TextSegment> embeddingStore;

    @Value("${rag.knowledge.pattern:classpath*:rag/knowledge/*.md}")
    private String knowledgePattern;

    @PostConstruct
    public void init() {
        loadKnowledgeBase();
    }

    public void loadKnowledgeBase() {
        // 1. 启动时扫描本地知识库文件。
        PathMatchingResourcePatternResolver resolver = new PathMatchingResourcePatternResolver();
        try {
            Resource[] resources = resolver.getResources(knowledgePattern);
            int loaded = 0;
            for (Resource resource : resources) {
                String content = readKnowledge(resource);
                if (content == null) {
                    continue;
                }

                // 2. 每篇知识文档都向量化后写入 store。
                addToVectorStore(content);
                loaded++;
            }
            log.info("RAG knowledge loaded: {} documents from pattern={}", loaded, knowledgePattern);
        } catch (IOException ex) {
            throw new IllegalStateException("failed to load rag knowledge resources", ex);
        }
    }

    private String readKnowledge(Resource resource) throws IOException {
        if (!resource.exists()) {
            return null;
        }
        try (var inputStream = resource.getInputStream()) {
            String content = new String(inputStream.readAllBytes(), StandardCharsets.UTF_8);
            return content.isBlank() ? null : content;
        }
    }

    private void addToVectorStore(String content) {
        Embedding embedding = embeddingService.embed(content);
        TextSegment segment = TextSegment.from(content);
        embeddingStore.add(embedding, segment);
    }
}
