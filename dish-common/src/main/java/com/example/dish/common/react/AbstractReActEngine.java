package com.example.dish.common.react;

import com.example.dish.common.agent.ReActState;
import com.example.dish.common.agent.ReActState.StepType;
import com.example.dish.common.context.AgentContext;

import java.util.Optional;

/**
 * ReAct 引擎抽象基类
 * <p>
 * 提供通用流程，子类只需实现特定方法：
 * - think(): 生成思考
 * - decideAction(): 决定行动
 * - executeAction(): 执行行动
 * - generateResponse(): 生成最终响应
 */
public abstract class AbstractReActEngine implements ReActEngine {

    protected final int maxIterations;

    protected AbstractReActEngine(int maxIterations) {
        this.maxIterations = maxIterations;
    }

    @Override
    public ReActResult execute(String userInput, AgentContext context) {
        ReActState state = createState(context.getSessionId(), userInput);

        while (shouldContinue(state)) {
            // 1. Thought: 分析当前状态
            String thought = think(state, context);
            state.addStep(StepType.THOUGHT, thought, getAgentName());

            // 2. Action: 决定行动
            Optional<Action> actionOpt = decideAction(state, context);
            if (actionOpt.isEmpty()) {
                // 无需更多行动，生成最终响应
                String response = generateResponse(state, context);
                state.addStep(StepType.FINAL, response, getAgentName());
                break;
            }

            Action action = actionOpt.get();
            state.addStep(StepType.ACTION, action.toString(), getAgentName());

            // 3. Observation: 执行并观察结果
            ObservationResult observation = executeAction(action, context);
            state.addStep(StepType.OBSERVATION, observation.content(), getAgentName());

            if (observation.isTerminal()) {
                state.addStep(StepType.FINAL, observation.finalResponse(), getAgentName());
                break;
            }

            state.nextIteration();
        }

        return buildResult(state, context);
    }

    @Override
    public boolean shouldContinue(ReActState state) {
        return !state.isComplete() && state.getCurrentIteration() < maxIterations;
    }

    @Override
    public int getMaxIterations() {
        return maxIterations;
    }

    // ===== 子类实现方法 =====

    /**
     * 创建初始状态
     */
    protected abstract ReActState createState(String sessionId, String userInput);

    /**
     * 思考步骤：分析当前状态
     */
    protected abstract String think(ReActState state, AgentContext context);

    /**
     * 决定下一步行动
     *
     * @return 行动（无行动则返回空）
     */
    protected abstract Optional<Action> decideAction(ReActState state, AgentContext context);

    /**
     * 执行行动
     */
    protected abstract ObservationResult executeAction(Action action, AgentContext context);

    /**
     * 生成最终响应
     */
    protected abstract String generateResponse(ReActState state, AgentContext context);

    /**
     * 构建结果
     */
    protected abstract ReActResult buildResult(ReActState state, AgentContext context);

    /**
     * 获取Agent名称
     */
    protected abstract String getAgentName();

    /**
     * 行动封装
     */
    public record Action(String type, String content) {
        @Override
        public String toString() {
            return type + ":" + content;
        }
    }

    /**
     * 观察结果
     */
    public record ObservationResult(String content, boolean isTerminal, String finalResponse) {
        public static ObservationResult of(String content) {
            return new ObservationResult(content, false, null);
        }

        public static ObservationResult terminal(String finalResponse) {
            return new ObservationResult(null, true, finalResponse);
        }
    }
}
