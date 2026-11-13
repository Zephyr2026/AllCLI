package com.allcli.gateway.admin;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.allcli.gateway.model.Invocation;
import com.allcli.gateway.model.ToolDefinition;
import com.allcli.gateway.persistence.InvocationRepository;
import com.allcli.gateway.persistence.ToolDefinitionRepository;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.LocalDateTime;
import java.util.Map;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.actuate.observability.AutoConfigureObservability;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

/**
 * 指标看板口径（FR-OPS-01 一期）。共享 DB 中有其他测试/E2E 残留行且其他测试类会写
 * invocation 不清理——故全部断言用「基线→seed→再读」的增量口径，保证确定性。
 *
 * <p>{@code @AutoConfigureObservability}：Boot 测试框架默认关 metrics export（注入
 * management.defaults.metrics.export.enabled=false），开启后 /actuator/prometheus 才可用
 * （生产运行时默认开启，不受影响）。
 */
@SpringBootTest
@AutoConfigureObservability
@AutoConfigureMockMvc
class AdminMetricsTest {

    private static final String ADMIN = "Bearer admin_m4-dev-admin";
    private static final String INV_PREFIX = "m4_meti_";
    private static final String TOOL_PREFIX = "m4_mett_";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private InvocationRepository invRepo;

    @Autowired
    private ToolDefinitionRepository toolRepo;

    private final ObjectMapper mapper = new ObjectMapper();

    @AfterEach
    void cleanup() {
        invRepo.findAll().stream()
                .filter(i -> i.getInvocationId().startsWith(INV_PREFIX))
                .forEach(invRepo::delete);
        toolRepo.findAll().stream()
                .filter(t -> t.getToolId().startsWith(TOOL_PREFIX))
                .forEach(toolRepo::delete);
    }

    @Test
    void invocationWindowDeltas() throws Exception {
        JsonNode before = metrics();

        LocalDateTime now = LocalDateTime.now().withNano(0);
        // 今日 4 条：3 成功（其中 1 条曾进 HITL——approvalId 非空）+ 1 失败；昨日 2 条
        invRepo.save(inv(INV_PREFIX + "t1", "m4_met_a1", "m4_mett_toolA", "success", 100, now.minusMinutes(5)));
        Invocation hitl = inv(INV_PREFIX + "t2", "m4_met_a2", "m4_mett_toolA", "success", 200, now.minusMinutes(4));
        hitl.setApprovalId("m4_meti_appr1");
        invRepo.save(hitl);
        invRepo.save(inv(INV_PREFIX + "t3", "m4_met_a1", "m4_mett_toolB", "success", 300, now.minusMinutes(3)));
        invRepo.save(inv(INV_PREFIX + "t4", "m4_met_a1", "m4_mett_toolB", "fail", 400, now.minusMinutes(2)));
        invRepo.save(inv(INV_PREFIX + "y1", "m4_met_a1", "m4_mett_toolA", "success", 50, now.minusDays(1)));
        invRepo.save(inv(INV_PREFIX + "y2", "m4_met_a2", "m4_mett_toolB", "success", 60, now.minusDays(1)));

        JsonNode after = metrics();
        assertThat(delta(after, before, s -> s.path("invocations").path("today").path("total"))).isEqualTo(4);
        assertThat(delta(after, before, s -> s.path("invocations").path("last7d").path("total"))).isEqualTo(6);
        assertThat(delta(after, before, s -> s.path("invocations").path("today").path("success"))).isEqualTo(3);
        assertThat(delta(after, before, s -> s.path("invocations").path("today").path("fail"))).isEqualTo(1);
        // HITL 口径：approvalId 非空（t2 已放行为 success 但 approvalId 保留）
        assertThat(delta(after, before, s -> s.path("invocations").path("today").path("hitl"))).isEqualTo(1);
        // 挂起中的（status=hitl_pending）：无
        assertThat(delta(after, before, s -> s.path("invocations").path("today").path("pendingApproval"))).isZero();
        assertThat(after.path("invocations").path("today").path("avgDurationMs").isNumber()).isTrue();
    }

    @Test
    void toolStatusDeltas() throws Exception {
        JsonNode before = metrics();
        seedTool(TOOL_PREFIX + "a");
        seedTool(TOOL_PREFIX + "b");
        seedTool(TOOL_PREFIX + "c");

        JsonNode after = metrics();
        assertThat(delta(after, before, s -> s.path("tools").path("draft"))).isEqualTo(3);
        assertThat(delta(after, before, s -> s.path("tools").path("total"))).isEqualTo(3);
        assertThat(delta(after, before, s -> s.path("tools").path("published"))).isZero();
        assertThat(after.path("systemCount").isLong() || after.path("systemCount").isInt()).isTrue();
    }

    @Test
    void reuseDeltas() throws Exception {
        JsonNode before = metrics();
        LocalDateTime now = LocalDateTime.now().withNano(0);
        // toolA 被 2 个 Agent 调用（复用），toolB 仅 1 个
        invRepo.save(inv(INV_PREFIX + "r1", "m4_met_a1", "m4_mett_rA", "success", 10, now.minusMinutes(1)));
        invRepo.save(inv(INV_PREFIX + "r2", "m4_met_a2", "m4_mett_rA", "success", 20, now.minusMinutes(1)));
        invRepo.save(inv(INV_PREFIX + "r3", "m4_met_a1", "m4_mett_rB", "success", 30, now.minusMinutes(1)));

        JsonNode after = metrics();
        assertThat(delta(after, before, s -> s.path("reuse").path("usedTools"))).isEqualTo(2);
        assertThat(after.path("reuse").path("reusedToolIds").toString()).contains("m4_mett_rA");
        assertThat(after.path("reuse").path("reusedToolIds").toString()).doesNotContain("m4_mett_rB");
        assertThat(after.path("reuse").path("reusedTools").asLong())
                .isGreaterThanOrEqualTo(before.path("reuse").path("reusedTools").asLong() + 1);
    }

    @Test
    void prometheusEndpointExposed() throws Exception {
        // 一期内网免鉴权口径（不在 admin 拦截器范围）
        MvcResult result = mockMvc.perform(get("/actuator/prometheus"))
                .andExpect(status().isOk())
                .andReturn();
        assertThat(result.getResponse().getContentAsString())
                .contains("jvm_");   // JVM 基础指标恒在；http_server_requests 需有流量后出现
    }

    private JsonNode metrics() throws Exception {
        MvcResult result = mockMvc.perform(get("/api/v1/metrics")
                        .header("Authorization", ADMIN))
                .andExpect(status().isOk())
                .andReturn();
        // ApiResponse 信封 {code,message,data}，指标在 data 下
        return mapper.readTree(result.getResponse()
                .getContentAsString(java.nio.charset.StandardCharsets.UTF_8)).path("data");
    }

    private static long delta(JsonNode after, JsonNode before, java.util.function.Function<JsonNode, JsonNode> pick) {
        return pick.apply(after).asLong() - pick.apply(before).asLong();
    }

    private void seedTool(String toolId) {
        toolRepo.save(ToolDefinition.builder()
                .toolId(toolId).name(toolId).version("1.0.0").systemRef("m4_metsys")
                .description("指标测试工具").sideEffect("readonly").reversibility("readonly")
                .endpoint(Map.of("method", "GET", "path", "/x"))
                .status("draft").owner("alice").build());
    }

    private static Invocation inv(String id, String agentId, String toolId,
                                  String status, int durationMs, LocalDateTime createdAt) {
        return Invocation.builder()
                .invocationId(id).traceId("m4_meti_trace_" + id).agentId(agentId).toolId(toolId)
                .inputDigest("d-" + id).outputDigest("o-" + id).status(status)
                .durationMs(durationMs).createdAt(createdAt)
                .build();
    }
}
