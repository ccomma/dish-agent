# ADR-001: 选择 Java/Spring 而非 Python 作为核心架构

## 状态
已接受（2026-04）

## 背景
餐饮 AI Agent 平台需要集成到已有的企业级餐饮 SaaS 系统（如二维火）。这些系统通常以 Java/Spring 为技术栈。需决策 AI Agent 层的技术栈选择。

## 决策
选择 **Java 17+ / Spring Boot 3.2 / Spring Cloud Alibaba / Dubbo 3.2** 作为核心架构。

## 理由

1. **与餐饮 SaaS 生态一致**：大多数餐饮 SaaS（二维火、客如云等）使用 Java/Spring，Agent 层用同样技术栈可降低集成成本。

2. **企业级基础设施**：Spring Cloud Alibaba + Dubbo 提供成熟的服务发现、RPC、熔断降级、配置中心，无需从零搭建分布式基础设施。

3. **多租户天然适配**：Spring Security + 自定义 filter 实现租户隔离，比 Python 生态更标准化。

4. **Agent 组件化**：Java 的接口 + DI 容器天然支持 Tool 插件化注册和 Agent 编排，比 Python 的动态导入更安全。

5. **国内招聘市场**：Java 工程师更易招聘，面试时展示 Java Agent 架构比 Python 更能打动国内技术负责人。

## 替代方案

**Python (LangChain/LangGraph)**：AI 生态更好但企业级基础设施弱；与已有 SaaS 系统集成需额外胶水层；不适合多租户 SaaS 部署。

**Go**：性能好但 AI/LLM 生态最弱；LangChain4j 等关键库无 Go 版本。

## 后果

- LangChain4j 替代 LangChain，API 设计理念相似但更 Java 化
- Dubbo RPC 替代 HTTP/gRPC，性能更好但要求 Dubbo 协议
- AI 相关库（Embedding、Reranker）需通过 LangChain4j 的适配层调用
- 部分前沿 AI 能力（如 ColPali）可能需要自建适配
