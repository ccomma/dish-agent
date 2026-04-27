package com.example.dish.memory.storage;

import com.example.dish.memory.support.HashEmbeddingModel;
import dev.langchain4j.model.embedding.EmbeddingModel;
import dev.langchain4j.model.openai.OpenAiEmbeddingModel;
import org.apache.commons.lang3.StringUtils;

/**
 * 长期记忆 EmbeddingModel 工厂。
 * 负责按配置创建 OpenAI 或本地 hash embedding 模型，避免存储编排类直接依赖模型构造细节。
 */
public class EmbeddingModelFactory {

    public EmbeddingModel create(
            String embeddingProvider,
            String openAiApiKey,
            String openAiBaseUrl,
            String openAiModel,
            int vectorDim
    ) {
        if ("openai".equalsIgnoreCase(embeddingProvider) && StringUtils.isNotBlank(openAiApiKey)) {
            return OpenAiEmbeddingModel.builder()
                    .apiKey(openAiApiKey)
                    .baseUrl(openAiBaseUrl)
                    .modelName(openAiModel)
                    .build();
        }
        return new HashEmbeddingModel(vectorDim);
    }
}
