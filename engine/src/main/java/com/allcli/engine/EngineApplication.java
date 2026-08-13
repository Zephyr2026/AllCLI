package com.allcli.engine;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * AllCLI 转化引擎入口。
 *
 * <p>转化逻辑（parser/mapper/model）为纯 Java，无 Spring 依赖；
 * Spring 仅作为 CLI runner 外壳 + JPA 持久化层存在。
 */
@SpringBootApplication
public class EngineApplication {

    public static void main(String[] args) {
        SpringApplication.run(EngineApplication.class, args);
    }
}
