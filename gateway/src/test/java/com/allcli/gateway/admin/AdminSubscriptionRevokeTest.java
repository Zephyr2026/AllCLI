package com.allcli.gateway.admin;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.allcli.gateway.model.Approval;
import com.allcli.gateway.model.Invocation;
import com.allcli.gateway.model.Subscription;
import com.allcli.gateway.model.ToolDefinition;
import com.allcli.gateway.persistence.ApprovalRepository;
import com.allcli.gateway.persistence.InvocationRepository;
import com.allcli.gateway.persistence.SubscriptionRepository;
import com.allcli.gateway.persistence.ToolDefinitionRepository;
import java.time.LocalDateTime;
import java.util.Map;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

/**
 * 订阅治理 + HITL 待办（admin 面）：待办列表、revoke=Kill Switch（invoke 立即被拒）、审批工作台数据源。
 */
@SpringBootTest
@AutoConfigureMockMvc
class AdminSubscriptionRevokeTest {

    private static final String PREFIX = "m4_sub_";
    private static final String AGENT = "m4_sub_agent";
    private static final String TOOL = "m4_sub_tool";
    private static final String ADMIN = "Bearer admin_m4-dev-admin";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private SubscriptionRepository subRepo;

    @Autowired
    private ApprovalRepository approvalRepo;

    @Autowired
    private InvocationRepository invRepo;

    @Autowired
    private ToolDefinitionRepository toolRepo;

    @BeforeEach
    void seed() {
        toolRepo.save(ToolDefinition.builder()
                .toolId(TOOL).name(TOOL).version("1.0.0").systemRef("m4_subsys")
                .description("订阅治理测试工具").sideEffect("readonly").reversibility("readonly")
                .endpoint(Map.of("method", "GET", "path", "/x"))
                .status("published").owner("alice").build());
        // pending 用另一 agent：invoke 鉴权按 agentId×toolId 唯一查询，不能撞行
        subRepo.save(sub(PREFIX + "pending", "pending", "m4_sub_other"));
        subRepo.save(sub(PREFIX + "approved", "approved", AGENT));
    }

    @AfterEach
    void cleanup() {
        subRepo.findAll().stream()
                .filter(s -> s.getSubId().startsWith(PREFIX))
                .forEach(subRepo::delete);
        approvalRepo.findAll().stream()
                .filter(a -> a.getApprovalId().startsWith(PREFIX))
                .forEach(approvalRepo::delete);
        invRepo.findAll().stream()
                .filter(i -> i.getInvocationId().startsWith(PREFIX))
                .forEach(invRepo::delete);
        toolRepo.findAll().stream()
                .filter(t -> t.getToolId().startsWith(TOOL))
                .forEach(toolRepo::delete);
    }

    @Test
    void pendingListFiltersByStatus() throws Exception {
        mockMvc.perform(get("/api/v1/subscriptions")
                        .header("Authorization", ADMIN)
                        .param("status", "pending"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("OK"))
                .andExpect(jsonPath("$.data.total").value(1))
                .andExpect(jsonPath("$.data.items[0].subId").value(PREFIX + "pending"));

        // agent token 不可访问管理面列表
        mockMvc.perform(get("/api/v1/subscriptions")
                        .header("Authorization", "Bearer agent_" + AGENT)
                        .param("status", "pending"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("AUTH_DENIED"));
    }

    @Test
    void revokeBlocksSubsequentInvokes() throws Exception {
        // revoke 前：invoke 过鉴权（工具 endpoint 不可达，预期 UPSTREAM 失败而非 AUTH_DENIED）
        String before = invoke();
        assertThat(before).isNotEqualTo("AUTH_DENIED");

        mockMvc.perform(post("/api/v1/subscriptions/" + PREFIX + "approved" + "/revoke")
                        .header("Authorization", ADMIN))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("OK"))
                .andExpect(jsonPath("$.data.status").value("revoked"));

        // Kill Switch：同订阅 invoke 立即被鉴权拦截
        assertThat(invoke()).isEqualTo("AUTH_DENIED");

        // 再次 revoke（终态）→ INVALID_STATUS_TRANSITION
        mockMvc.perform(post("/api/v1/subscriptions/" + PREFIX + "approved" + "/revoke")
                        .header("Authorization", ADMIN))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("INVALID_STATUS_TRANSITION"));
    }

    @Test
    void approvalPendingListEnrichesFromInvocation() throws Exception {
        Invocation inv = invRepo.save(Invocation.builder()
                .invocationId(PREFIX + "inv1").traceId(PREFIX + "trace").agentId(AGENT).toolId(TOOL)
                .status("hitl_pending").durationMs(10).createdAt(LocalDateTime.now().withNano(0))
                .build());
        approvalRepo.save(Approval.builder()
                .approvalId(PREFIX + "appr1").invocationId(inv.getInvocationId())
                .approverRef("agent_owner").decision("pending").createdAt(LocalDateTime.now().withNano(0))
                .build());

        mockMvc.perform(get("/api/v1/approvals")
                        .header("Authorization", ADMIN))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("OK"))
                .andExpect(jsonPath("$.data.items[0].approvalId").value(PREFIX + "appr1"))
                .andExpect(jsonPath("$.data.items[0].toolId").value(TOOL))
                .andExpect(jsonPath("$.data.items[0].agentId").value(AGENT))
                .andExpect(jsonPath("$.data.items[0].approverRef").value("agent_owner"));
    }

    private String invoke() throws Exception {
        return com.jayway.jsonpath.JsonPath.read(
                mockMvc.perform(post("/api/v1/invoke")
                                .header("Authorization", "Bearer agent_" + AGENT)
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("{\"toolName\":\"" + TOOL + "\",\"arguments\":{}}"))
                        .andExpect(status().isOk())
                        .andReturn().getResponse()
                        .getContentAsString(java.nio.charset.StandardCharsets.UTF_8),
                "$.code");
    }

    private static Subscription sub(String subId, String status, String agentId) {
        return Subscription.builder()
                .subId(subId).agentId(agentId).toolId(TOOL).scope(null)
                .status(status).build();
    }
}
