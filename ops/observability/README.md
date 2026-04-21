# Observability Starter

这个目录提供一套可直接演示的观测启动资源：

- `docker-compose.yml`：一键启动 Prometheus、Grafana、OTLP Collector 和 Tempo
- `prometheus/prometheus.yml`：抓取网关、control plane、agent cluster 的 `/actuator/prometheus`
- `grafana/dashboards/dish-agent-mission-control.json`：默认 Mission Control Dashboard
- `otel-collector/config.yaml`：接收应用 OTLP traces 并转发到 Tempo
- `tempo/tempo.yml`：本地 trace 存储与查询配置

## 启动方式

```bash
cd ops/observability
docker compose up -d
```

启动后：

- Prometheus: `http://localhost:9090`
- Grafana: `http://localhost:3000`
  - 用户名：`admin`
  - 密码：`admin`
- Tempo: `http://localhost:3200`
- OTLP HTTP endpoint: `http://localhost:4318/v1/traces`

## Trace 规范

- HTTP 入口统一使用 `X-Trace-Id`
- Dubbo attachment 统一使用 `traceId`
- 日志模式统一输出 `traceId=%X{traceId}`
- Gateway 内部关键步骤额外输出 `gateway.step.dispatch`、`gateway.step.resume` span，Provider 侧通过 Dubbo attachment 自动恢复父 span

## 关键指标

- `dish_execution_started_total`
- `dish_execution_outcome_total`
- `dish_execution_node_transitions_total`
- `dish_execution_node_latency_seconds`
- `dish_execution_duration_seconds`
- `dish_execution_active`
- `dish_execution_running`
- `dish_execution_waiting_approval`
- `dish_execution_stream_subscribers`
- `dish_execution_approval_decisions_total`
