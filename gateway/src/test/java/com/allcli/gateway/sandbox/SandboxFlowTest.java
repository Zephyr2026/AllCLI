package com.allcli.gateway.sandbox;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.allcli.gateway.model.ToolDefinition;
import com.allcli.gateway.persistence.InvocationRepository;
import com.allcli.gateway.persistence.ToolDefinitionRepository;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.web.servlet.MockMvc;

/**
 * 沙箱试运行 + 质量自检（FR-AGENT-03 / FR-SEM-05）：
 * mock 响应生成、入参校验/回退、不写审计、draft 工具免订阅、token 门禁、checklist 判定。
 */
@SpringBootTest
@AutoConfigureMockMvc
class SandboxFlowTest {

    private static final String PREFIX = "sbx_";
    private static final String AUTH = "Bearer agent_sbxop";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ToolDefinitionRepository toolRepo;

    @Autowired
    private InvocationRepository invocationRepo;

    @BeforeEach
    void seed() {
        toolRepo.save(readonlyTool("sbx_list"));        // draft，含完整 schema
        toolRepo.save(writeTool("sbx_create"));         // write 全配齐（含 examples）
        ToolDefinition unlabeled = writeTool("sbx_unlabeled");
        unlabeled.setReversibility("readonly");         // write 未标注可逆性
        unlabeled.setIdempotencyKey(null);
        unlabeled.setHitl(Map.of("enabled", false));
        unlabeled.setExamples(null);
        toolRepo.save(unlabeled);
        ToolDefinition bare = readonlyTool("sbx_bare");
        bare.setDescription("abc");                     // 描述过短
        toolRepo.save(bare);
    }

    @AfterEach
    void cleanup() {
        toolRepo.findAll().stream()
                .filter(t -> t.getToolId().startsWith(PREFIX))
                .forEach(toolRepo::delete);
    }

    @Test
    void sandboxWithExplicitArgumentsReturnsMockResult() throws Exception {
        mockMvc.perform(post("/api/v1/tools/sbx_list/sandbox")
                        .header("Authorization", AUTH)
                        .contentType("application/json")
                        .content("{\"arguments\":{\"limit\":7}}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("OK"))
                .andExpect(jsonPath("$.data.sandbox").value(true))
                .andExpect(jsonPath("$.data.status").value("succeeded"))
                .andExpect(jsonPath("$.data.argSource").value("request"))
                .andExpect(jsonPath("$.data.arguments.limit").value(7))
                .andExpect(jsonPath("$.data.result[0].id").value(42))
                .andExpect(jsonPath("$.data.result[0].name").value("mock_name"));
    }

    @Test
    void sandboxWithoutArgumentsFallsBackToGenerated() throws Exception {
        mockMvc.perform(post("/api/v1/tools/sbx_list/sandbox")
                        .header("Authorization", AUTH))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.argSource").value("generated"))
                .andExpect(jsonPath("$.data.arguments.limit").value(42));
    }

    @Test
    void sandboxUsesFirstExampleWhenPresent() throws Exception {
        mockMvc.perform(post("/api/v1/tools/sbx_create/sandbox")
                        .header("Authorization", AUTH))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.argSource").value("example"))
                .andExpect(jsonPath("$.data.arguments.name").value("doggie"));
    }

    @Test
    void sandboxRejectsInvalidArguments() throws Exception {
        mockMvc.perform(post("/api/v1/tools/sbx_list/sandbox")
                        .header("Authorization", AUTH)
                        .contentType("application/json")
                        .content("{\"arguments\":{\"limit\":\"not-an-int\"}}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("INTENT_REJECTED"));
    }

    @Test
    void sandboxRequiresAgentToken() throws Exception {
        mockMvc.perform(post("/api/v1/tools/sbx_list/sandbox"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("AUTH_DENIED"));
    }

    @Test
    void sandboxWritesNoInvocationAndNeedsNoSubscription() throws Exception {
        // sbx_* 工具从未建立任何订阅（draft），仍可试运行 —— 上线前自检场景
        long before = invocationRepo.count();
        mockMvc.perform(post("/api/v1/tools/sbx_list/sandbox")
                        .header("Authorization", AUTH))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("succeeded"));
        assertThat(invocationRepo.count()).isEqualTo(before);
    }

    @Test
    void qualityReadonlyToolWarnsOnlyOnExamples() throws Exception {
        mockMvc.perform(post("/api/v1/tools/sbx_list/quality")
                        .header("Authorization", AUTH))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.overall").value("warn"))
                .andExpect(jsonPath("$.data.items[?(@.item=='description')].status").value("pass"))
                .andExpect(jsonPath("$.data.items[?(@.item=='inputSchema')].status").value("pass"))
                .andExpect(jsonPath("$.data.items[?(@.item=='examples')].status").value("warn"))
                .andExpect(jsonPath("$.data.items[?(@.item=='writeGuard')].status").value("pass"));
    }

    @Test
    void qualityFullyConfiguredWriteToolPassesExamples() throws Exception {
        mockMvc.perform(post("/api/v1/tools/sbx_create/quality")
                        .header("Authorization", AUTH))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.overall").value("pass"))
                .andExpect(jsonPath("$.data.items[?(@.item=='examples')].status").value("pass"))
                .andExpect(jsonPath("$.data.items[?(@.item=='writeGuard')].status").value("pass"));
    }

    @Test
    void qualityUnlabeledWriteToolFailsWriteGuard() throws Exception {
        mockMvc.perform(post("/api/v1/tools/sbx_unlabeled/quality")
                        .header("Authorization", AUTH))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.overall").value("fail"))
                .andExpect(jsonPath("$.data.items[?(@.item=='writeGuard')].status").value("fail"));
    }

    @Test
    void qualityShortDescriptionFails() throws Exception {
        mockMvc.perform(post("/api/v1/tools/sbx_bare/quality")
                        .header("Authorization", AUTH))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.overall").value("fail"))
                .andExpect(jsonPath("$.data.items[?(@.item=='description')].status").value("fail"));
    }

    @Test
    void qualityRequiresAgentToken() throws Exception {
        mockMvc.perform(post("/api/v1/tools/sbx_list/quality"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("AUTH_DENIED"));
    }

    private ToolDefinition readonlyTool(String toolId) {
        return ToolDefinition.builder()
                .toolId(toolId)
                .name(toolId)
                .version("1.0.0")
                .status("draft")
                .systemRef("sysSbx")
                .description("沙箱测试只读工具")
                .endpoint(Map.of("method", "GET", "path", "/" + toolId, "timeoutMs", 3000))
                .inputSchema(Map.of(
                        "type", "object",
                        "properties", Map.of("limit", Map.of("type", "integer")),
                        "required", List.of()))
                .outputSchema(Map.of(
                        "200", Map.of(
                                "type", "array",
                                "items", Map.of(
                                        "type", "object",
                                        "properties", Map.of(
                                                "id", Map.of("type", "integer"),
                                                "name", Map.of("type", "string"))))))
                .sideEffect("readonly")
                .reversibility("readonly")
                .hitl(Map.of("enabled", false))
                .examples(null)
                .owner("sbx_owner")
                .build();
    }

    private ToolDefinition writeTool(String toolId) {
        return ToolDefinition.builder()
                .toolId(toolId)
                .name(toolId)
                .version("1.0.0")
                .status("draft")
                .systemRef("sysSbx")
                .description("沙箱测试写工具")
                .endpoint(Map.of("method", "POST", "path", "/" + toolId, "timeoutMs", 3000))
                .inputSchema(Map.of(
                        "type", "object",
                        "properties", Map.of("name", Map.of("type", "string")),
                        "required", List.of("name")))
                .outputSchema(Map.of(
                        "200", Map.of(
                                "type", "object",
                                "properties", Map.of(
                                        "id", Map.of("type", "integer"),
                                        "name", Map.of("type", "string")))))
                .sideEffect("write")
                .reversibility("reversible")
                .idempotencyKey("client_request_id")
                .hitl(Map.of("enabled", true))
                .examples(List.of(Map.of("arguments", Map.of("name", "doggie"))))
                .owner("sbx_owner")
                .build();
    }
}
