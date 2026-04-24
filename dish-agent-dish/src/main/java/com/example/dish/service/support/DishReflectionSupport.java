package com.example.dish.service.support;

import com.example.dish.common.agent.ReActState;
import com.example.dish.common.context.AgentContext;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;
import java.util.stream.Stream;

/**
 * 菜品 Agent reflection 支撑。
 * 负责维护 reflection 开关、重试标记、答案充分性判断和 query 扩写规则，避免这些细节堆在 ReAct 引擎里。
 */
@Component
public class DishReflectionSupport {

    private static final String REFLECTION_META_KEY = "enableReflection";
    private static final String RETRIED_META_KEY = "retried";

    public boolean isReflectionEnabled(AgentContext context) {
        if (context.getMetadata() == null) {
            return false;
        }
        Object value = context.getMetadata().get(REFLECTION_META_KEY);
        return value instanceof Boolean b && b;
    }

    public boolean hasRetried(AgentContext context) {
        if (context.getMetadata() == null) {
            return false;
        }
        Object value = context.getMetadata().get(RETRIED_META_KEY);
        return value instanceof Boolean b && b;
    }

    public void markRetried(AgentContext context) {
        Map<String, Object> metadata = context.getMetadata();
        if (metadata != null) {
            metadata.put(RETRIED_META_KEY, true);
        }
    }

    public String latestObservation(ReActState state) {
        List<ReActState.ReActStep> steps = state.getSteps();
        for (int i = steps.size() - 1; i >= 0; i--) {
            ReActState.ReActStep step = steps.get(i);
            if (step.getType() == ReActState.StepType.OBSERVATION) {
                return step.getContent();
            }
        }
        return null;
    }

    public boolean isSufficient(String answer, String question) {
        // 1. 太短或明显失败的答案，直接视为不足。
        if (answer == null || answer.trim().length() < 20) {
            return false;
        }
        String[] vaguePhrases = {"未找到", "不知道", "无法回答", "没有相关信息"};
        for (String phrase : vaguePhrases) {
            if (answer.contains(phrase)) {
                return false;
            }
        }

        // 2. 再用问题关键词覆盖率做一个轻量判断，决定是否需要 reflection 重试。
        String[] questionKeywords = question.replaceAll("[的么了是]", "").split("[\\s，、？?！!]");
        long keywordCount = Stream.of(questionKeywords).filter(keyword -> !keyword.isBlank()).count();
        if (keywordCount == 0) {
            return true;
        }
        long matchCount = Stream.of(questionKeywords)
                .filter(keyword -> !keyword.isBlank())
                .filter(answer::contains)
                .count();
        return matchCount >= Math.max(1, keywordCount / 2);
    }

    public String expandQuery(String originalQuery) {
        // 根据问句类型补充更完整的检索维度，提升第二次召回命中率。
        StringBuilder expanded = new StringBuilder(originalQuery);
        if (originalQuery.contains("什么")) {
            expanded.append("，包括做法、配料、口味、营养等");
        } else if (originalQuery.contains("怎么") || originalQuery.contains("如何")) {
            expanded.append("，包括具体步骤、技巧、注意事项等");
        } else if (originalQuery.contains("多少") || originalQuery.contains("价格")) {
            expanded.append("，包括价格、份量、热量等");
        }
        return expanded.toString();
    }
}
