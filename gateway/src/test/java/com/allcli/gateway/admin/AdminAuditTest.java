package com.allcli.gateway.admin;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.allcli.gateway.model.Invocation;
import com.allcli.gateway.model.ToolDefinition;
import com.allcli.gateway.persistence.InvocationRepository;
import com.allcli.gateway.persistence.ToolDefinitionRepository;
import java.time.LocalDateTime;
import java.util.Map;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

/** 审计查询 + CSV 导出（FR-GOV-01，admin 面）。 */
@SpringBootTest
@AutoConfigureMockMvc
class AdminAuditTest {

    private static final String ADMIN = "Bearer admin_m4-dev-admin";
    private static final String AGENT = "m4_auda_agent";
    private static final String TOOL_REV = "m4_audt_rev";
    private static final String TOOL_IRR = "m4_audt_irr";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private InvocationRepository invocationRepo;

    @Autowired
    private ToolDefinitionRepository toolRepo;

    @BeforeEach
    void seed() {
        toolRepo.save(ToolDefinition.builder()
                .toolId(TOOL_REV).name("rev").version("1.0.0").systemRef("m4_audsys")
                .description("可逆写").sideEffect("write").reversibility("reversible")
                .endpoint(Map.of("method", "POST", "path", "/rev"))
                .status("draft").owner("alice").build());
        toolRepo.save(ToolDefinition.builder()
                .toolId(TOOL_IRR).name("irr").version("1.0.0").systemRef("m4_audsys")
                .description("不可逆写").sideEffect("write").reversibility("irreversible")
                .endpoint(Map.of("method", "DELETE", "path", "/irr"))
                .status("draft").owner("alice").build());

        LocalDateTime base = LocalDateTime.now().withNano(0);
        invocationRepo.save(inv("m4_auda_i1", AGENT, TOOL_REV, "success", 100, base.minusMinutes(10)));
        invocationRepo.save(inv("m4_auda_i2", AGENT, TOOL_IRR, "success", 200, base.minusMinutes(9)));
        // 带 approvalId：曾进入 HITL 后放行（upsert 后 status 已是 success）
        Invocation hitl = inv("m4_auda_i3", AGENT, TOOL_IRR, "success", 300, base.minusMinutes(8));
        hitl.setApprovalId("m4_auda_appr");
        invocationRepo.save(hitl);
        invocationRepo.save(inv("m4_auda_i4", "other_agent", TOOL_REV, "fail", 400, base.minusMinutes(7)));
        invocationRepo.save(inv("m4_auda_i5", AGENT, TOOL_REV, "success", 500, base.minusDays(2)));
    }

    @AfterEach
    void cleanup() {
        invocationRepo.findAll().stream()
                .filter(i -> i.getAgentId().equals(AGENT) || i.getAgentId().equals("other_agent"))
                .filter(i -> i.getInvocationId().startsWith("m4_auda_"))
                .forEach(invocationRepo::delete);
        toolRepo.findAll().stream()
                .filter(t -> t.getToolId().startsWith("m4_audt_"))
                .forEach(toolRepo::delete);
    }

    @Test
    void filtersByAgentToolAndTimeWindowSortedDesc() throws Exception {
        LocalDateTime from = LocalDateTime.now().minusHours(1);
        LocalDateTime to = LocalDateTime.now().plusMinutes(1);
        mockMvc.perform(get("/api/v1/audit")
                        .header("Authorization", ADMIN)
                        .param("agentId", AGENT)
                        .param("toolId", TOOL_REV)
                        .param("from", from.toString())
                        .param("to", to.toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("OK"))
                .andExpect(jsonPath("$.data.total").value(1))
                .andExpect(jsonPath("$.data.items[0].invocationId").value("m4_auda_i1"))
                .andExpect(jsonPath("$.data.items[0].inputDigest").doesNotExist());

        // 不按 agent 过滤（用自有 toolId 限定，不受其他测试/E2E 残留数据影响）：
        // 时间窗内 TOOL_REV 有 i4+i1 两条，按 createdAt 倒序
        mockMvc.perform(get("/api/v1/audit")
                        .header("Authorization", ADMIN)
                        .param("toolId", TOOL_REV)
                        .param("from", from.toString())
                        .param("to", to.toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.total").value(2))
                .andExpect(jsonPath("$.data.items[0].invocationId").value("m4_auda_i4"))
                .andExpect(jsonPath("$.data.items[1].invocationId").value("m4_auda_i1"));
    }

    @Test
    void filtersByReversibilityViaToolSubquery() throws Exception {
        mockMvc.perform(get("/api/v1/audit")
                        .header("Authorization", ADMIN)
                        .param("agentId", AGENT)
                        .param("reversibility", "irreversible"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.total").value(2))
                .andExpect(jsonPath("$.data.items[0].invocationId").value("m4_auda_i3"))
                .andExpect(jsonPath("$.data.items[1].invocationId").value("m4_auda_i2"));
    }

    @Test
    void exportCsvHasBomHeaderAndRows() throws Exception {
        MvcResult result = mockMvc.perform(get("/api/v1/audit/export.csv")
                        .header("Authorization", ADMIN)
                        .param("agentId", AGENT))
                .andExpect(status().isOk())
                .andReturn();

        assertThat(result.getResponse().getContentType()).contains("text/csv");
        String body = result.getResponse().getContentAsString(java.nio.charset.StandardCharsets.UTF_8);
        assertThat(body).startsWith("﻿");   // UTF-8 BOM
        assertThat(body).contains("createdAt,invocationId,traceId,agentId,toolId,status");
        assertThat(body).contains("m4_auda_i1").contains("m4_auda_i5");
        // 不含 digest 列（导出列白名单里没有）
        assertThat(body).doesNotContain("inputDigest").doesNotContain("outputDigest");
    }

    private static Invocation inv(String invocationId, String agentId, String toolId,
                                  String status, int durationMs, LocalDateTime createdAt) {
        return Invocation.builder()
                .invocationId(invocationId)
                .traceId("m4_auda_trace_" + invocationId)
                .agentId(agentId)
                .toolId(toolId)
                .inputDigest("d-" + invocationId)
                .outputDigest("o-" + invocationId)
                .status(status)
                .durationMs(durationMs)
                .createdAt(createdAt)
                .build();
    }
}
