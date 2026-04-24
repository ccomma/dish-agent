package com.example.dish.memory;

import org.apache.dubbo.config.spring.context.annotation.EnableDubbo;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.client.discovery.EnableDiscoveryClient;

@SpringBootApplication(scanBasePackages = "com.example.dish")
@EnableDiscoveryClient
@EnableDubbo
/**
 * dish-memory 服务启动入口。
 */
public class MemoryApplication {

    public static void main(String[] args) {
        SpringApplication.run(MemoryApplication.class, args);
    }
}
