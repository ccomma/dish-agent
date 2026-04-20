package com.example.dish.common.runtime;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;

/**
 * 执行计划（DAG）。
 */
public class ExecutionPlan implements Serializable {

    private final String planId;
    private final String intent;
    private final List<ExecutionNode> nodes;
    private final List<ExecutionEdge> edges;
    private final Map<String, Object> metadata;

    private ExecutionPlan(Builder builder) {
        this.planId = builder.planId;
        this.intent = builder.intent;
        this.nodes = builder.nodes != null ? new ArrayList<>(builder.nodes) : Collections.emptyList();
        this.edges = builder.edges != null ? new ArrayList<>(builder.edges) : Collections.emptyList();
        this.metadata = builder.metadata != null ? Map.copyOf(builder.metadata) : Collections.emptyMap();
    }

    public static Builder builder() {
        return new Builder();
    }

    public String planId() {
        return planId;
    }

    public String intent() {
        return intent;
    }

    public List<ExecutionNode> nodes() {
        return nodes;
    }

    public List<ExecutionEdge> edges() {
        return edges;
    }

    public Map<String, Object> metadata() {
        return metadata;
    }

    public static class Builder {
        private String planId;
        private String intent;
        private List<ExecutionNode> nodes;
        private List<ExecutionEdge> edges;
        private Map<String, Object> metadata;

        public Builder planId(String planId) {
            this.planId = planId;
            return this;
        }

        public Builder intent(String intent) {
            this.intent = intent;
            return this;
        }

        public Builder nodes(List<ExecutionNode> nodes) {
            this.nodes = nodes;
            return this;
        }

        public Builder edges(List<ExecutionEdge> edges) {
            this.edges = edges;
            return this;
        }

        public Builder metadata(Map<String, Object> metadata) {
            this.metadata = metadata;
            return this;
        }

        public ExecutionPlan build() {
            return new ExecutionPlan(this);
        }
    }
}
