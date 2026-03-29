package com.enterprise.langchain4j;

import com.enterprise.langchain4j.agent.OrchestrationAgent;

/**
 * 多Agent协同架构 - 启动入口
 *
 * 架构说明：
 * - OrchestrationAgent: 编排入口，负责协调
 * - RoutingAgent: 前置路由，意图识别与参数抽取
 * - DishKnowledgeAgent: 菜品知识，RAG检索增强
 * - WorkOrderAgent: 工单处理，业务工具调用
 *
 * 协作流程：
 * 用户输入 → OrchestrationAgent → RoutingAgent → 分发到对应Agent
 */
public class Bootstrap {

    public static void main(String[] args) {
        System.out.println("╔══════════════════════════════════════════╗");
        System.out.println("║   餐饮智能助手 - 多Agent协同架构      ║");
        System.out.println("╠══════════════════════════════════════════╣");
        System.out.println("║  前置路由 | 菜品知识 | 工单处理        ║");
        System.out.println("╚══════════════════════════════════════════╝");
        System.out.println();

        OrchestrationAgent agent = new OrchestrationAgent();
        agent.chat();
    }
}
