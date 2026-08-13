package com.allcli.gateway.core;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.allcli.gateway.api.dto.GatewayException;
import com.allcli.gateway.api.dto.HitlDecisionRequest;
import com.allcli.gateway.api.dto.InvokeData;
import com.allcli.gateway.api.dto.InvokeRequest;
import com.allcli.gateway.model.Approval;
import com.allcli.gateway.model.Subscription;
import com.allcli.gateway.model.System;
import com.allcli.gateway.model.ToolDefinition;
import com.allcli.gateway.persistence.ApprovalRepository;
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
 * HITL 全流程集成测试：write(reversible/irreversible) 挂起 → decision → 续跑/拒绝。
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE)
class HitlFlowTest {

    private static HttpServer mockServer;
    private static String mockBaseUrl;

    @Autowired private InvokePipeline pipeline;
    @Autowired private HitlService hitlService;
    @Autowired private ToolDefinitionRepository toolRepo;
    @Autowired private SystemRepository systemRepo;
    @Autowired private SubscriptionRepository subRepo;
    @Autowired private ApprovalRepository approvalRepo;
    @Autowired private com.allcli.gateway.persistence.InvocationRepository invRepo;

    private static final String SYSTEM_ID = "gw-hitl-system";
    private static final String AGENT = "gw-hitl-agent";
    private static final String APPROVER = "alice";

    @BeforeAll
    static void startMock() throws IOException {
        mockServer = HttpServer.create(new InetSocketAddress(0), 0);
        mockServer.createContext("/pets", exchange -> {
            byte[] resp = "{\"id\":1,\"name\":\"Rex\",\"token\":\"sensitive\"}".getBytes();
            exchange.getResponseHeaders().set("Content-Type", "application/json;charset=utf-8");
            exchange.sendResponseHeaders(201, resp.length);
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
        approvalRepo.deleteAll(approvalRepo.findAll());
        subRepo.deleteAll(subRepo.findAll());
        toolRepo.findAll().stream()
                .filter(t -> t.getToolId() != null && t.getToolId().startsWith("gw_hitl_"))
                .forEach(toolRepo::delete);
        systemRepo.findAll().stream()
                .filter(s -> SYSTEM_ID.equals(s.getSystemId()))
                .forEach(systemRepo::delete);
    }

    private String seedTool(String toolId, String reversibility) {
        systemRepo.save(System.builder()
                .systemId(SYSTEM_ID).name("hitl").baseUrl(mockBaseUrl)
                .authType("None").owner(APPROVER).status("active").build());
        toolRepo.save(ToolDefinition.builder()
                .toolId(toolId).name(toolId).version("1.0.0").status("published")
                .systemRef(SYSTEM_ID).description("hitl tool")
                .endpoint(Map.of("method", "POST", "path", "/pets", "timeoutMs", 30000))
                .inputSchema(Map.of("type", "object"))
                .sideEffect("write").reversibility(reversibility)
                .hitl(Map.of("enabled", true, "approverType", "owner", "approverRef", APPROVER, "timeoutSec", 600))
                .owner(APPROVER).build());
        subRepo.save(Subscription.builder()
                .subId("sub-" + toolId).agentId(AGENT).toolId(toolId).status("approved")
                .approvedBy(APPROVER).build());
        return toolId;
    }

    @Test
    void writeReversibleSuspendsThenApprovesAndRuns() {
        String toolId = seedTool("gw_hitl_create", "reversible");

        // 第一次 invoke → 挂起
        InvokeData pending = pipeline.execute("trace-1", AGENT, InvokeRequest.builder()
                .toolName(toolId).arguments(Map.of("name", "Rex")).idempotencyKey("k1").build());
        assertThat(pending.getStatus()).isEqualTo("pending_approval");
        assertThat(pending.getApprovalId()).startsWith("apv_");

        // Approval 状态 pending
        Approval apv = hitlService.findApproval(pending.getApprovalId());
        assertThat(apv.getDecision()).isEqualTo("pending");

        // 审批 approved → 续跑
        hitlService.decide(pending.getApprovalId(), new HitlDecisionRequest("approved", "ok", null), APPROVER);
        InvokeData done = pipeline.resume(pending.getApprovalId());
        assertThat(done.getStatus()).isEqualTo("succeeded");
        assertThat(done.getInvocationId()).isEqualTo(pending.getInvocationId());
        // 结果脱敏：token 字段应被置 ***
        assertThat(done.getResult().toString()).contains("***").doesNotContain("sensitive");

        // 审计：应有一条 success 记录
        List<com.allcli.gateway.model.Invocation> rows = invRepo.findAll();
        assertThat(rows).anyMatch(r -> "success".equals(r.getStatus())
                && toolId.equals(r.getToolId())
                && pending.getApprovalId().equals(r.getApprovalId()));
    }

    @Test
    void irreversibleRejectsWithoutConfirm() {
        String toolId = seedTool("gw_hitl_delete", "irreversible");

        InvokeData pending = pipeline.execute("trace-2", AGENT, InvokeRequest.builder()
                .toolName(toolId).arguments(Map.of()).idempotencyKey("k2").build());
        assertThat(pending.getStatus()).isEqualTo("pending_approval");

        // approved 但未带 confirmIrreversible → 拒绝
        assertThatThrownBy(() -> hitlService.decide(pending.getApprovalId(),
                new HitlDecisionRequest("approved", "ok", null), APPROVER))
                .isInstanceOf(GatewayException.class);

        // 带 confirmIrreversible=true → 通过
        hitlService.decide(pending.getApprovalId(),
                new HitlDecisionRequest("approved", "ok", true), APPROVER);
        InvokeData done = pipeline.resume(pending.getApprovalId());
        assertThat(done.getStatus()).isEqualTo("succeeded");
    }

    @Test
    void rejectedDecisionFails() {
        String toolId = seedTool("gw_hitl_create2", "reversible");
        InvokeData pending = pipeline.execute("trace-3", AGENT, InvokeRequest.builder()
                .toolName(toolId).arguments(Map.of()).idempotencyKey("k3").build());

        assertThatThrownBy(() -> hitlService.decide(pending.getApprovalId(),
                new HitlDecisionRequest("rejected", "no", null), APPROVER))
                .isInstanceOf(GatewayException.class);
    }
}
