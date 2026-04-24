package com.example.dish;

import org.apache.dubbo.config.spring.context.annotation.EnableDubbo;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.client.discovery.EnableDiscoveryClient;

/**
 * 闲聊Agent启动类
 *
 * 职责：
 * 1. 提供简单对话服务
 * 2. 通过 Dubbo 对外提供服务
 */
@SpringBootApplication(scanBasePackages = "com.example.dish")
@EnableDiscoveryClient
@EnableDubbo
public class ChatAgentApplication {

    public static void main(String[] args) {
        SpringApplication.run(ChatAgentApplication.class, args);
    }
}
