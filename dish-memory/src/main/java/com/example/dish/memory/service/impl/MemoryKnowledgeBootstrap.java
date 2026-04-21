package com.example.dish.memory.service.impl;

import com.example.dish.control.memory.model.MemoryLayer;
import com.example.dish.control.memory.model.MemoryWriteRequest;
import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.Resource;
import org.springframework.core.io.support.PathMatchingResourcePatternResolver;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Map;

@Component
public class MemoryKnowledgeBootstrap {

    private static final Logger log = LoggerFactory.getLogger(MemoryKnowledgeBootstrap.class);

    @Value("${memory.long-term.bootstrap.enabled:true}")
    private boolean bootstrapEnabled = true;

    @Value("${memory.long-term.bootstrap.pattern:classpath*:memory/knowledge/*.md}")
    private String knowledgePattern = "classpath*:memory/knowledge/*.md";

    @Autowired
    private MemoryWriteServiceImpl memoryWriteService;

    @PostConstruct
    void init() {
        if (!bootstrapEnabled) {
            return;
        }

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

                String sourceName = resource.getFilename() != null ? resource.getFilename() : "knowledge.md";
                boolean ok = memoryWriteService.write(new MemoryWriteRequest(
                        "GLOBAL",
                        "KNOWLEDGE_BOOTSTRAP",
                        MemoryLayer.LONG_TERM_KNOWLEDGE,
                        "knowledge_bootstrap",
                        content,
                        Map.of(
                                "sessionId", "KNOWLEDGE_BOOTSTRAP",
                                "storeId", "GLOBAL",
                                "sourceFile", sourceName,
                                "bootstrap", true,
                                "knowledgeScope", "global"
                        ),
                        "TRC-BOOTSTRAP-KNOWLEDGE"
                ));
                if (ok) {
                    loaded++;
                }
            }
            log.info("memory long-term knowledge bootstrapped: loaded={}, pattern={}", loaded, knowledgePattern);
        } catch (IOException ex) {
            throw new IllegalStateException("failed to bootstrap long-term memory knowledge", ex);
        }
    }
}
