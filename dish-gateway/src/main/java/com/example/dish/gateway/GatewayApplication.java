package com.example.dish.gateway;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.client.discovery.EnableDiscoveryClient;

/**
 * 网关服务启动类
 *
 * 职责：
 * 1. 接收用户 HTTP 请求
 * 2. 调用 RoutingAgent 进行意图识别
 * 3. 通过 Dubbo RPC 调用各 Agent 服务
 * 4. 聚合结果返回给客户端
 */
@SpringBootApplication
@EnableDiscoveryClient
public class GatewayApplication {

    public static void main(String[] args) {
        SpringApplication.run(GatewayApplication.class, args);
    }
}
