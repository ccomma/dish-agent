package com.example.dish;

import org.apache.dubbo.config.spring.context.annotation.EnableDubbo;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.client.discovery.EnableDiscoveryClient;

/**
 * 工单处理Agent启动类
 *
 * 职责：
 * 1. 提供库存/订单/退款查询服务
 * 2. 实现 ReAct 多步推理
 * 3. 通过 Dubbo 对外提供服务
 */
@SpringBootApplication(scanBasePackages = "com.example.dish")
@EnableDiscoveryClient
@EnableDubbo
public class WorkOrderAgentApplication {

    public static void main(String[] args) {
        SpringApplication.run(WorkOrderAgentApplication.class, args);
    }
}
