package com.example.dish.service;

import com.example.dish.common.agent.ReActState;
import com.example.dish.common.classifier.IntentType;
import com.example.dish.common.context.AgentContext;
import com.example.dish.common.react.AbstractReActEngine;
import com.example.dish.common.react.ReActEngine;
import com.example.dish.rag.RAGPipeline;
import com.example.dish.service.support.DishReflectionSupport;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import javax.annotation.Resource;
import java.util.Optional;

/**
 * 菜品知识 ReAct 执行器。
 */
@Component
public class DishReActEngine extends AbstractReActEngine {

    private static final Logger log = LoggerFactory.getLogger(DishReActEngine.class);

    @Resource
    private RAGPipeline ragPipeline;
    @Resource
    private DishReflectionSupport dishReflectionSupport;

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
        // 1. 首轮根据意图决定检索方向，帮助状态机形成清晰的“当前思考”。
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
        // 2. 二轮以后重点判断首轮检索是否足以回答用户问题。
        return "评估首次检索结果是否足够回答用户问题";
    }

    @Override
    protected Optional<Action> decideAction(ReActState state, AgentContext context) {
        // 1. 首次还没有 observation 时，先做一次标准 RAG 检索。
        long observationCount = state.getSteps().stream()
                .filter(step -> step.getType() == ReActState.StepType.OBSERVATION)
                .count();
        if (observationCount == 0) {
            return Optional.of(new Action("RAG_retrieve", state.getOriginalInput()));
        }

        // 2. 未开启 reflection 或已经补检过一次时，直接结束动作决策。
        if (!dishReflectionSupport.isReflectionEnabled(context) || dishReflectionSupport.hasRetried(context)) {
            return Optional.empty();
        }

        // 3. 首次结果不足时，扩写 query 后再重试一次。
        String latestObservation = dishReflectionSupport.latestObservation(state);
        if (dishReflectionSupport.isSufficient(latestObservation, state.getOriginalInput())) {
            return Optional.empty();
        }

        dishReflectionSupport.markRetried(context);
        return Optional.of(new Action("expand_query", dishReflectionSupport.expandQuery(state.getOriginalInput())));
    }

    @Override
    protected ObservationResult executeAction(Action action, AgentContext context) {
        try {
            // 统一委托 RAGPipeline 执行检索与生成。
            String result = ragPipeline.answer(action.content());
            return ObservationResult.of(result);
        } catch (Exception ex) {
            log.error("dish react action failed: type={}, query={}", action.type(), action.content(), ex);
            return ObservationResult.terminal("抱歉，当前知识检索服务暂时不可用，请稍后重试。");
        }
    }

    @Override
    protected String generateResponse(ReActState state, AgentContext context) {
        String observation = dishReflectionSupport.latestObservation(state);
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
}
