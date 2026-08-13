package com.allcli.gateway;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * AllCLI 工具网关入口。
 *
 * <p>invoke 流水线（core/stages）为纯 Java 编排，Spring 提供 MVC + JPA + Redis 基础设施。
 * 技术栈为 Spring Boot MVC（非响应式 SCG），因 invoke 是编排式业务流水线而非反向代理路由。
 */
@SpringBootApplication
public class GatewayApplication {

    public static void main(String[] args) {
        SpringApplication.run(GatewayApplication.class, args);
    }
}
