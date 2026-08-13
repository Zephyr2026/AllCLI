package com.allcli.gateway.core;

import static org.assertj.core.api.Assertions.assertThat;

import com.allcli.gateway.api.dto.InvokeData;
import com.allcli.gateway.api.dto.InvokeRequest;
import com.allcli.gateway.model.Subscription;
import com.allcli.gateway.model.System;
import com.allcli.gateway.model.ToolDefinition;
import com.allcli.gateway.persistence.SubscriptionRepository;
import com.allcli.gateway.persistence.SystemRepository;
import com.allcli.gateway.persistence.ToolDefinitionRepository;
import com.sun.net.httpserver.HttpServer;
import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

/**
 * readonly 直放链路集成测试：起内嵌 mock 下游，端到端验证 invoke→转发→审计。
 *
 * <p>连真实 MySQL（192.168.85.128/allcli）与 Redis。
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE)
class InvokePipelineReadOnlyTest {

    private static HttpServer mockServer;
    private static String mockBaseUrl;

    @Autowired
    private InvokePipeline pipeline;
    @Autowired
    private ToolDefinitionRepository toolRepo;
    @Autowired
    private SystemRepository systemRepo;
    @Autowired
    private SubscriptionRepository subRepo;
    @Autowired
    private com.allcli.gateway.persistence.InvocationRepository invRepo;

    private static final String SYSTEM_ID = "gw-test-system";
    private static final String TOOL_ID = "gw_test_list";
    private static final String AGENT = "gw-test-agent";

    @BeforeAll
    static void startMock() throws IOException {
        mockServer = HttpServer.create(new InetSocketAddress(0), 0);
        mockServer.createContext("/pets", exchange -> {
            byte[] resp = "[{\"id\":1,\"name\":\"Rex\"}]".getBytes();
            exchange.getResponseHeaders().set("Content-Type", "application/json;charset=utf-8");
            exchange.sendResponseHeaders(200, resp.length);
            try (OutputStream os = exchange.getResponseBody()) {
                os.write(resp);
            }
        });
        mockServer.start();
        mockBaseUrl = "http://127.0.0.1:" + mockServer.getAddress().getPort();
    }

    @AfterAll
    static void stopMock() {
        if (mockServer != null) {
            mockServer.stop(0);
        }
    }

    @AfterEach
    void cleanup() {
        invRepo.deleteAll(invRepo.findAll());
        subRepo.deleteAll(subRepo.findAll());
        toolRepo.findAll().stream()
                .filter(t -> TOOL_ID.equals(t.getToolId()))
                .forEach(toolRepo::delete);
        systemRepo.findAll().stream()
                .filter(s -> SYSTEM_ID.equals(s.getSystemId()))
                .forEach(systemRepo::delete);
    }

    @Test
    void readonlyInvokeIsForwardedAndAudited() {
        // 前置数据
        systemRepo.save(System.builder()
                .systemId(SYSTEM_ID).name("test").baseUrl(mockBaseUrl)
                .authType("None").owner("alice").status("active").build());
        toolRepo.save(ToolDefinition.builder()
                .toolId(TOOL_ID).name(TOOL_ID).version("1.0.0").status("published")
                .systemRef(SYSTEM_ID).description("test tool")
                .endpoint(Map.of("method", "GET", "path", "/pets", "timeoutMs", 30000))
                .inputSchema(Map.of("type", "object"))
                .outputSchema(Map.of("200", Map.of("type", "array")))
                .sideEffect("readonly").reversibility("readonly")
                .owner("alice").build());
        subRepo.save(Subscription.builder()
                .subId("sub1").agentId(AGENT).toolId(TOOL_ID).status("approved")
                .approvedBy("alice").build());

        InvokeRequest req = InvokeRequest.builder()
                .toolName(TOOL_ID).arguments(Map.of("limit", 1)).build();

        InvokeData data = pipeline.execute("trace-test", AGENT, req);

        assertThat(data.getStatus()).isEqualTo("succeeded");
        assertThat(data.getResult()).asString().contains("Rex");
        assertThat(data.getInvocationId()).startsWith("inv_");

        // 审计落库：1 行 success
        List<com.allcli.gateway.model.Invocation> rows = invRepo.findAll();
        assertThat(rows).anyMatch(r -> "success".equals(r.getStatus())
                && TOOL_ID.equals(r.getToolId())
                && AGENT.equals(r.getAgentId())
                && r.getInputDigest() != null
                && r.getOutputDigest() != null);
    }
}
