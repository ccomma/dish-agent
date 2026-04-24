package com.example.dish.memory.service.impl;

import com.example.dish.control.memory.model.MemoryLayer;
import com.example.dish.control.memory.model.MemoryWriteRequest;
import jakarta.annotation.PostConstruct;
import org.apache.commons.lang3.StringUtils;
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
/**
 * 长期知识预热器。
 * 服务启动后会扫描 `memory/knowledge/*.md`，并把知识样例写入长期记忆层，
 * 这样本地演示和控制台第一次打开时就能看到可检索的长期知识。
 */
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
        // 1. 允许通过配置关闭预热，避免联调或测试场景重复写入。
        if (!bootstrapEnabled) {
            return;
        }

        PathMatchingResourcePatternResolver resolver = new PathMatchingResourcePatternResolver();
        try {
            Resource[] resources = resolver.getResources(knowledgePattern);
            int loaded = 0;
            for (Resource resource : resources) {
                // 2. 逐个读取知识文件，空文件或不存在文件直接跳过。
                if (!resource.exists()) {
                    continue;
                }
                String content;
                try (var inputStream = resource.getInputStream()) {
                    content = new String(inputStream.readAllBytes(), StandardCharsets.UTF_8);
                }
                if (StringUtils.isBlank(content)) {
                    continue;
                }

                String sourceName = resource.getFilename() != null ? resource.getFilename() : "knowledge.md";
                // 3. 统一按 GLOBAL/KNOWLEDGE_BOOTSTRAP 会话写入长期知识层，保留来源元数据。
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
            // 4. 记录本次预热结果，便于启动日志快速确认是否加载成功。
            log.info("memory long-term knowledge bootstrapped: loaded={}, pattern={}", loaded, knowledgePattern);
        } catch (IOException ex) {
            throw new IllegalStateException("failed to bootstrap long-term memory knowledge", ex);
        }
    }
}
