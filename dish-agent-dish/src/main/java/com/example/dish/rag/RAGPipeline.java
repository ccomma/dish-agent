package com.example.dish.rag;

import com.example.dish.service.EmbeddingService;
import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.model.input.PromptTemplate;
import com.example.dish.rag.support.RagRetriever;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.Map;

/**
 * RAG 管道：向量检索 + 可选重排 + LLM 生成。
 */
@Component
public class RAGPipeline {

    @Autowired
    private ChatModel chatModel;
    @Autowired
    private RagRetriever ragRetriever;

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
        // 1. 先完成检索与上下文拼装。
        String context = ragRetriever.retrieveContext(question, 3);
        // 2. 再套入回答模板，约束模型只基于参考信息回答。
        String prompt = ANSWER_TEMPLATE.apply(Map.of("context", context, "question", question)).text();
        // 3. 最后交给 chatModel 生成自然语言回复。
        return chatModel.chat(prompt);
    }
}
