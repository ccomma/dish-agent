package com.enterprise.langchain4j.agent;

import com.enterprise.langchain4j.context.AgentContext;

import java.util.ArrayList;
import java.util.List;

/**
 * ReAct 模式状态机
 *
 * 实现 Thought → Action → Observation 循环
 */
public class ReActState {

    /**
     * ReAct 步骤类型
     */
    public enum StepType {
        THOUGHT,   // 思考：分析当前状态，决定下一步
        ACTION,    // 行动：执行某个操作
        OBSERVATION, // 观察：获取行动结果
        FINAL      // 最终响应
    }

    /**
     * 单个 ReAct 步骤
     */
    public static class ReActStep {
        private final StepType type;
        private final String content;
        private final String agentName;
        private final long timestamp;

        public ReActStep(StepType type, String content, String agentName) {
            this.type = type;
            this.content = content;
            this.agentName = agentName;
            this.timestamp = System.currentTimeMillis();
        }

        public StepType getType() { return type; }
        public String getContent() { return content; }
        public String getAgentName() { return agentName; }
        public long getTimestamp() { return timestamp; }

        @Override
        public String toString() {
            return String.format("[%s|%s] %s", type, agentName, content);
        }
    }

    private final String sessionId;
    private final String originalInput;
    private final List<ReActStep> steps;
    private final List<AgentContext> contextHistory;
    private int maxIterations;
    private int currentIteration;
    private boolean isComplete;

    public ReActState(String sessionId, String originalInput) {
        this.sessionId = sessionId;
        this.originalInput = originalInput;
        this.steps = new ArrayList<>();
        this.contextHistory = new ArrayList<>();
        this.maxIterations = 5;
        this.currentIteration = 0;
        this.isComplete = false;
    }

    public void addStep(StepType type, String content, String agentName) {
        steps.add(new ReActStep(type, content, agentName));
        if (type == StepType.FINAL) {
            this.isComplete = true;
        }
    }

    public void addContext(AgentContext context) {
        this.contextHistory.add(context);
    }

    public void nextIteration() {
        this.currentIteration++;
    }

    public boolean shouldContinue() {
        return !isComplete && currentIteration < maxIterations;
    }

    public String getSessionId() { return sessionId; }
    public String getOriginalInput() { return originalInput; }
    public List<ReActStep> getSteps() { return steps; }
    public List<AgentContext> getContextHistory() { return contextHistory; }
    public int getCurrentIteration() { return currentIteration; }
    public boolean isComplete() { return isComplete; }

    public void setMaxIterations(int maxIterations) { this.maxIterations = maxIterations; }

    /**
     * 获取最终的响应内容（最后一个 FINAL 步骤）
     */
    public String getFinalResponse() {
        for (int i = steps.size() - 1; i >= 0; i--) {
            ReActStep step = steps.get(i);
            if (step.getType() == StepType.FINAL) {
                return step.getContent();
            }
        }
        return "无法生成最终响应";
    }

    /**
     * 打印 ReAct 执行轨迹（用于调试）
     */
    public void printTrace() {
        System.out.println("\n═══ ReAct 执行轨迹 ═══");
        System.out.println("会话: " + sessionId);
        System.out.println("原始输入: " + originalInput);
        System.out.println("迭代次数: " + currentIteration);
        System.out.println("-".repeat(40));
        for (ReActStep step : steps) {
            System.out.println(step);
        }
        System.out.println("═".repeat(40) + "\n");
    }
}