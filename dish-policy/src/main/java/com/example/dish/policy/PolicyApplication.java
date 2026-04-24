package com.example.dish.policy;

import org.apache.dubbo.config.spring.context.annotation.EnableDubbo;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.client.discovery.EnableDiscoveryClient;

@SpringBootApplication(scanBasePackages = "com.example.dish")
@EnableDiscoveryClient
@EnableDubbo
/**
 * dish-policy 服务启动入口。
 */
public class PolicyApplication {

    public static void main(String[] args) {
        SpringApplication.run(PolicyApplication.class, args);
    }
}
