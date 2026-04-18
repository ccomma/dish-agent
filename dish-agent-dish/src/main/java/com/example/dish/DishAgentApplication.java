package com.example.dish;

import org.apache.dubbo.config.spring.context.annotation.EnableDubbo;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.client.discovery.EnableDiscoveryClient;

/**
 * 菜品知识Agent启动类
 *
 * 职责：
 * 1. 提供 RAG 检索服务
 * 2. 实现 ReAct 多步推理
 * 3. 通过 Dubbo 对外提供服务
 */
@SpringBootApplication
@EnableDiscoveryClient
@EnableDubbo
public class DishAgentApplication {

    public static void main(String[] args) {
        SpringApplication.run(DishAgentApplication.class, args);
    }
}
