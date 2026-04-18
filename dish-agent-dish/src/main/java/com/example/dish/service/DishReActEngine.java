package com.example.dish.service;

import com.example.dish.common.agent.ReActState;
import com.example.dish.common.classifier.IntentType;
import com.example.dish.common.context.AgentContext;
import com.example.dish.common.react.AbstractReActEngine;
import com.example.dish.common.react.ReActEngine;
import com.example.dish.rag.RAGPipeline;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import javax.annotation.Resource;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Stream;

/**
 * 菜品知识 ReAct 执行器。
 */
@Component
public class DishReActEngine extends AbstractReActEngine {

    private static final Logger log = LoggerFactory.getLogger(DishReActEngine.class);
    private static final String REFLECTION_META_KEY = "enableReflection";
    private static final String RETRIED_META_KEY = "retried";

    @Resource
    private RAGPipeline ragPipeline;

    public DishReActEngine() {
        super(5);
    }

    @Override
    protected ReActState createState(String sessionId, String userInput) {
        ReActState state = new ReActState(sessionId, userInput);
        state.setMaxIterations(getMaxIterations());
        return state;
    }

    @Override
    protected String think(ReActState state, AgentContext context) {
        if (state.getSteps().isEmpty()) {
            IntentType intent = context.getIntent();
            if (intent == null) {
                return "无法确定意图，执行通用知识检索";
            }
            return switch (intent) {
                case DISH_QUESTION -> "用户询问菜品信息，执行菜品检索";
                case DISH_INGREDIENT -> "用户询问菜品成分，执行成分检索";
                case DISH_COOKING_METHOD -> "用户询问做法，执行烹饪步骤检索";
                case POLICY_QUESTION -> "用户询问政策规则，执行政策检索";
                default -> "非结构化意图，执行通用检索";
            };
        }
        return "评估首次检索结果是否足够回答用户问题";
    }

    @Override
    protected Optional<Action> decideAction(ReActState state, AgentContext context) {
        long observationCount = state.getSteps().stream()
                .filter(step -> step.getType() == ReActState.StepType.OBSERVATION)
                .count();
        if (observationCount == 0) {
            return Optional.of(new Action("RAG_retrieve", state.getOriginalInput()));
        }

        if (!isReflectionEnabled(context) || hasRetried(context)) {
            return Optional.empty();
        }

        String latestObservation = latestObservation(state);
        if (isSufficient(latestObservation, state.getOriginalInput())) {
            return Optional.empty();
        }

        markRetried(context);
        return Optional.of(new Action("expand_query", expandQuery(state.getOriginalInput())));
    }

    @Override
    protected ObservationResult executeAction(Action action, AgentContext context) {
        try {
            String result = ragPipeline.answer(action.content());
            return ObservationResult.of(result);
        } catch (Exception ex) {
            log.error("dish react action failed: type={}, query={}", action.type(), action.content(), ex);
            return ObservationResult.terminal("抱歉，当前知识检索服务暂时不可用，请稍后重试。");
        }
    }

    @Override
    protected String generateResponse(ReActState state, AgentContext context) {
        String observation = latestObservation(state);
        return observation == null || observation.isBlank() ? "抱歉，暂未检索到可用信息。" : observation;
    }

    @Override
    protected ReActEngine.ReActResult buildResult(ReActState state, AgentContext context) {
        return new ReActEngine.ReActResult(state.getFinalResponse(), state, true);
    }

    @Override
    protected String getAgentName() {
        return "DishAgent";
    }

    private boolean isReflectionEnabled(AgentContext context) {
        if (context.getMetadata() == null) {
            return false;
        }
        Object value = context.getMetadata().get(REFLECTION_META_KEY);
        return value instanceof Boolean b && b;
    }

    private boolean hasRetried(AgentContext context) {
        if (context.getMetadata() == null) {
            return false;
        }
        Object value = context.getMetadata().get(RETRIED_META_KEY);
        return value instanceof Boolean b && b;
    }

    private void markRetried(AgentContext context) {
        Map<String, Object> metadata = context.getMetadata();
        if (metadata != null) {
            metadata.put(RETRIED_META_KEY, true);
        }
    }

    private String latestObservation(ReActState state) {
        List<ReActState.ReActStep> steps = state.getSteps();
        for (int i = steps.size() - 1; i >= 0; i--) {
            ReActState.ReActStep step = steps.get(i);
            if (step.getType() == ReActState.StepType.OBSERVATION) {
                return step.getContent();
            }
        }
        return null;
    }

    private boolean isSufficient(String answer, String question) {
        if (answer == null || answer.trim().length() < 20) {
            return false;
        }
        String[] vaguePhrases = {"未找到", "不知道", "无法回答", "没有相关信息"};
        for (String phrase : vaguePhrases) {
            if (answer.contains(phrase)) {
                return false;
            }
        }

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

    private String expandQuery(String originalQuery) {
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
