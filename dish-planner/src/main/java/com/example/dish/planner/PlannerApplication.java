package com.example.dish.planner;

import org.apache.dubbo.config.spring.context.annotation.EnableDubbo;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.client.discovery.EnableDiscoveryClient;

@SpringBootApplication(scanBasePackages = "com.example.dish")
@EnableDiscoveryClient
@EnableDubbo
/**
 * dish-planner 服务启动入口。
 */
public class PlannerApplication {

    public static void main(String[] args) {
        SpringApplication.run(PlannerApplication.class, args);
    }
}
