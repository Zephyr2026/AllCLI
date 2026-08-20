package com.allcli.gateway.mcp;

import java.util.List;
import io.modelcontextprotocol.server.McpServerFeatures;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * MCP 工具装配：以 List&lt;SyncToolSpecification&gt; bean 交给 spring-ai 自动配置
 * （McpServerAutoConfiguration 收集后注册进 McpSyncServer，tools/list 即此清单）。
 */
@Configuration
public class McpToolConfig {

    @Bean
    public List<McpServerFeatures.SyncToolSpecification> allcliToolSpecifications(
            McpToolRegistry registry) {
        return registry.buildSpecifications();
    }
}
