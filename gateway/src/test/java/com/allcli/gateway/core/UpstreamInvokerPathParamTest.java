package com.allcli.gateway.core;

import static org.assertj.core.api.Assertions.assertThat;

import com.allcli.gateway.api.dto.HitlDecisionRequest;
import com.allcli.gateway.api.dto.InvokeData;
import com.allcli.gateway.api.dto.InvokeRequest;
import com.allcli.gateway.model.Approval;
import com.allcli.gateway.model.Subscription;
import com.allcli.gateway.model.System;
import com.allcli.gateway.model.ToolDefinition;
import com.allcli.gateway.persistence.SubscriptionRepository;
import com.allcli.gateway.persistence.SystemRepository;
import com.allcli.gateway.persistence.ToolDefinitionRepository;
import com.allcli.gateway.persistence.ApprovalRepository;
import com.sun.net.httpserver.HttpServer;
import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

/**
 * 路径参数转发回归测试（UpstreamInvoker）：endpoint 形如 /pets/{petId} 时，
 * 路径变量须从 arguments 展开进 URL（HITL 续跑同样），GET 剩余参数转 query，路径变量不进 body。
 *
 * <p>连真实 MySQL（192.168.85.128/allcli）与 Redis；下游为内嵌录制式 mock。
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE)
class UpstreamInvokerPathParamTest {

    private static HttpServer mockServer;
    private static String mockBaseUrl;
    private static final List<String> recorded = new ArrayList<>();

    @Autowired private InvokePipeline pipeline;
    @Autowired private HitlService hitlService;
    @Autowired private ToolDefinitionRepository toolRepo;
    @Autowired private SystemRepository systemRepo;
    @Autowired private SubscriptionRepository subRepo;
    @Autowired private ApprovalRepository approvalRepo;
    @Autowired private com.allcli.gateway.persistence.InvocationRepository invRepo;

    private static final String SYSTEM_ID = "gw-path-system";
    private static final String AGENT = "gw-path-agent";
    private static final String APPROVER = "alice";

    @BeforeAll
    static void startMock() throws IOException {
        mockServer = HttpServer.create(new InetSocketAddress(0), 0);
        mockServer.createContext("/pets", exchange -> {
            recorded.add(exchange.getRequestMethod() + " "
                    + exchange.getRequestURI().getPath()
                    + (exchange.getRequestURI().getQuery() != null ? "?" + exchange.getRequestURI().getQuery() : ""));
            byte[] resp = "{\"ok\":true}".getBytes();
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
        recorded.clear();
        invRepo.deleteAll(invRepo.findAll());
        approvalRepo.deleteAll(approvalRepo.findAll());
        subRepo.deleteAll(subRepo.findAll());
        toolRepo.findAll().stream()
                .filter(t -> t.getToolId() != null && t.getToolId().startsWith("gw_path_"))
                .forEach(toolRepo::delete);
        systemRepo.findAll().stream()
                .filter(s -> SYSTEM_ID.equals(s.getSystemId()))
                .forEach(systemRepo::delete);
    }

    private void seedSystem() {
        systemRepo.save(System.builder()
                .systemId(SYSTEM_ID).name("path").baseUrl(mockBaseUrl)
                .authType("None").owner(APPROVER).status("active").build());
        subRepo.save(Subscription.builder()
                .subId("sub-path").agentId(AGENT).toolId("gw_path_get").status("approved")
                .approvedBy(APPROVER).build());
        subRepo.save(Subscription.builder()
                .subId("sub-path-del").agentId(AGENT).toolId("gw_path_delete").status("approved")
                .approvedBy(APPROVER).build());
    }

    @Test
    void readonlyGetExpandsPathParamAndForwardsQuery() {
        seedSystem();
        toolRepo.save(ToolDefinition.builder()
                .toolId("gw_path_get").name("gw_path_get").version("1.0.0").status("published")
                .systemRef(SYSTEM_ID).description("get pet")
                .endpoint(Map.of("method", "GET", "path", "/pets/{petId}", "timeoutMs", 30000))
                .inputSchema(Map.of("type", "object"))
                .sideEffect("readonly").reversibility("readonly")
                .owner(APPROVER).build());

        InvokeData data = pipeline.execute("trace-path-1", AGENT, InvokeRequest.builder()
                .toolName("gw_path_get")
                .arguments(Map.of("petId", "p1", "limit", 5))
                .build());

        assertThat(data.getStatus()).isEqualTo("succeeded");
        // 路径展开 + 剩余参数转 query
        assertThat(recorded).anyMatch(r -> r.equals("GET /pets/p1?limit=5"));
    }

    @Test
    void hitlResumeExpandsPathParamOnDelete() {
        seedSystem();
        toolRepo.save(ToolDefinition.builder()
                .toolId("gw_path_delete").name("gw_path_delete").version("1.0.0").status("published")
                .systemRef(SYSTEM_ID).description("delete pet")
                .endpoint(Map.of("method", "DELETE", "path", "/pets/{petId}", "timeoutMs", 30000))
                .inputSchema(Map.of("type", "object"))
                .sideEffect("write").reversibility("irreversible")
                .hitl(Map.of("enabled", true, "approverType", "owner", "approverRef", APPROVER, "timeoutSec", 600))
                .owner(APPROVER).build());

        // 挂起
        InvokeData pending = pipeline.execute("trace-path-2", AGENT, InvokeRequest.builder()
                .toolName("gw_path_delete")
                .arguments(Map.of("petId", "p9"))
                .idempotencyKey("path-k1")
                .build());
        assertThat(pending.getStatus()).isEqualTo("pending_approval");

        // 重复提交同参数：返回同一 invocation/approval，不重复挂起
        InvokeData dup = pipeline.execute("trace-path-2b", AGENT, InvokeRequest.builder()
                .toolName("gw_path_delete")
                .arguments(Map.of("petId", "p9"))
                .idempotencyKey("path-k1-dup")
                .build());
        assertThat(dup.getStatus()).isEqualTo("pending_approval");
        assertThat(dup.getApprovalId()).isEqualTo(pending.getApprovalId());
        assertThat(dup.getInvocationId()).isEqualTo(pending.getInvocationId());

        // 审批 → 续跑：路径参数在 resume 中展开
        hitlService.decide(pending.getApprovalId(),
                new HitlDecisionRequest("approved", "ok", true), APPROVER);
        InvokeData done = pipeline.resume(pending.getApprovalId());

        assertThat(done.getStatus()).isEqualTo("succeeded");
        assertThat(recorded).anyMatch(r -> r.equals("DELETE /pets/p9"));
        assertThat(recorded).noneMatch(r -> r.contains("{petId}"));

        // 已放行后再发同参数 → 幂等重放（IDEMPOTENT_REPLAY）
        org.assertj.core.api.Assertions.assertThatThrownBy(() ->
                        pipeline.execute("trace-path-2c", AGENT, InvokeRequest.builder()
                                .toolName("gw_path_delete")
                                .arguments(Map.of("petId", "p9"))
                                .build()))
                .isInstanceOf(com.allcli.gateway.api.dto.GatewayException.class)
                .hasMessageContaining("幂等命中");
    }
}
