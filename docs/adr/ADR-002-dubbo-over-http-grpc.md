# ADR-002: 选择 Dubbo RPC 而非 HTTP/gRPC 作为服务间通信

## 状态
已接受（2026-04）

## 背景
微服务架构下的服务间通信选型：Gateway ↔ Control Plane ↔ Agent Cluster 之间需要高性能、类型安全的 RPC 调用。

## 决策
选择 **Apache Dubbo 3.2 + Nacos** 作为服务间通信协议。

## 理由

1. **Spring Cloud Alibaba 深度集成**：与项目基础架构天然匹配，配置简单。

2. **高性能二进制协议**：Dubbo 协议比 HTTP/JSON 性能高 2-3x，对 Agent 推理延迟敏感场景有益。

3. **服务治理成熟**：内置负载均衡、熔断、限流、服务降级、超时重试等企业级能力。

4. **Java 原生接口契约**：通过共享 `dish-control-api` 模块定义接口，编译时类型安全，比 REST API 更可靠。

5. **Trace 透传**：Dubbo attachment 天然支持 traceId 等上下文传递。

## 替代方案

**HTTP/REST + OpenFeign**：更通用但性能较差，缺少二进制协议优势；不适合内部 Agent 高频调用。

**gRPC**：性能好但 Java 生态不如 Dubbo 成熟；需要额外 Protobuf 维护成本；与 Spring Cloud Alibaba 集成不够紧密。

## 后果

- 所有微服务间调用必须通过 Dubbo 接口
- 外部 HTTP 请求 → Gateway → 内部 Dubbo → 返回 HTTP 响应
- 接口契约集中在 `dish-control-api`，变更影响所有消费方
- Dubbo 协议调试比 HTTP 复杂，需工具支持
