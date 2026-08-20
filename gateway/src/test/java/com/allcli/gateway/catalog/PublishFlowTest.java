package com.allcli.gateway.catalog;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.allcli.gateway.catalog.event.ToolCatalogChangedEvent;
import com.allcli.gateway.model.ToolDefinition;
import com.allcli.gateway.model.ToolVersion;
import com.allcli.gateway.persistence.ToolDefinitionRepository;
import com.allcli.gateway.persistence.ToolVersionRepository;
import java.util.Map;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.event.ApplicationEvents;
import org.springframework.test.context.event.RecordApplicationEvents;
import org.springframework.test.web.servlet.MockMvc;

/** publish/deprecate（FR-REG-01）：快照、状态机、MISSING_REVERSIBILITY 门禁、事件。 */
@SpringBootTest
@AutoConfigureMockMvc
@RecordApplicationEvents
class PublishFlowTest {

    private static final String PREFIX = "cat_p_";
    private static final String AUTH = "Bearer agent_catop";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ToolDefinitionRepository toolRepo;

    @Autowired
    private ToolVersionRepository versionRepo;

    @Autowired
    private ApplicationEvents events;

    @BeforeEach
    void seed() {
        toolRepo.save(tool("cat_p_list", "readonly", "readonly"));              // 可发布
        toolRepo.save(tool("cat_p_unlabeled", "write", "readonly"));           // write 未标注可逆性
        ToolDefinition published = tool("cat_p_done", "readonly", "readonly");
        published.setStatus("published");
        toolRepo.save(published);
        versionRepo.save(ToolVersion.builder()
                .toolId("cat_p_done").version("1.0.0").definitionSnapshot(Map.of("k", "v"))
                .createdAt(java.time.LocalDateTime.now()).build());
    }

    @AfterEach
    void cleanup() {
        toolRepo.findAll().stream()
                .filter(t -> t.getToolId().startsWith(PREFIX))
                .forEach(toolRepo::delete);
        versionRepo.findAll().stream()
                .filter(v -> v.getToolId().startsWith(PREFIX))
                .forEach(versionRepo::delete);
    }

    @Test
    void publishWritesSnapshotAndFiresEvent() throws Exception {
        mockMvc.perform(post("/api/v1/tools/cat_p_list/publish")
                        .header("Authorization", AUTH)
                        .contentType("application/json")
                        .content("{\"target\":\"published\",\"changelog\":\"init\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("OK"))
                .andExpect(jsonPath("$.data.status").value("published"))
                .andExpect(jsonPath("$.data.snapshotId").exists());

        assertThat(toolRepo.findByToolId("cat_p_list").orElseThrow().getStatus())
                .isEqualTo("published");
        ToolVersion snap = versionRepo.findByToolIdAndVersion("cat_p_list", "1.0.0").orElseThrow();
        assertThat(snap.getChangelog()).isEqualTo("init");
        assertThat(snap.getDefinitionSnapshot())
                .containsEntry("toolId", "cat_p_list")
                .containsEntry("status", "published")
                .doesNotContainKey("id");

        assertThat(events.stream(ToolCatalogChangedEvent.class)
                .filter(e -> "cat_p_list".equals(e.toolId())
                        && ToolCatalogChangedEvent.ACTION_PUBLISHED.equals(e.action()))
                .count()).isGreaterThanOrEqualTo(1);
    }

    @Test
    void writeToolWithoutReversibilityIsRejected() throws Exception {
        mockMvc.perform(post("/api/v1/tools/cat_p_unlabeled/publish")
                        .header("Authorization", AUTH)
                        .contentType("application/json")
                        .content("{\"target\":\"published\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("MISSING_REVERSIBILITY"));
        assertThat(versionRepo.findByToolIdAndVersion("cat_p_unlabeled", "1.0.0")).isEmpty();
    }

    @Test
    void duplicateVersionSnapshotRejected() throws Exception {
        mockMvc.perform(post("/api/v1/tools/cat_p_done/publish")
                        .header("Authorization", AUTH)
                        .contentType("application/json")
                        .content("{\"target\":\"published\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("TOOL_VERSION_EXISTS"));
    }

    @Test
    void grayRequiresPercentInRange() throws Exception {
        mockMvc.perform(post("/api/v1/tools/cat_p_list/publish")
                        .header("Authorization", AUTH)
                        .contentType("application/json")
                        .content("{\"target\":\"gray\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));

        mockMvc.perform(post("/api/v1/tools/cat_p_list/publish")
                        .header("Authorization", AUTH)
                        .contentType("application/json")
                        .content("{\"target\":\"gray\",\"grayPercent\":101}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));
    }

    @Test
    void deprecateThenRepublishRejected() throws Exception {
        mockMvc.perform(post("/api/v1/tools/cat_p_list/deprecate")
                        .header("Authorization", AUTH))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("deprecated"));
        assertThat(events.stream(ToolCatalogChangedEvent.class)
                .filter(e -> ToolCatalogChangedEvent.ACTION_DEPRECATED.equals(e.action()))
                .count()).isGreaterThanOrEqualTo(1);

        mockMvc.perform(post("/api/v1/tools/cat_p_list/publish")
                        .header("Authorization", AUTH)
                        .contentType("application/json")
                        .content("{\"target\":\"published\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("INVALID_STATUS_TRANSITION"));
    }

    @Test
    void publishRequiresAgentToken() throws Exception {
        mockMvc.perform(post("/api/v1/tools/cat_p_list/publish")
                        .contentType("application/json")
                        .content("{\"target\":\"published\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("AUTH_DENIED"));
    }

    private ToolDefinition tool(String toolId, String sideEffect, String reversibility) {
        return ToolDefinition.builder()
                .toolId(toolId)
                .name(toolId)
                .version("1.0.0")
                .status("draft")
                .systemRef("sysA")
                .description("publish 测试工具")
                .endpoint(Map.of("method", "GET", "path", "/" + toolId, "timeoutMs", 3000))
                .sideEffect(sideEffect)
                .reversibility(reversibility)
                .hitl(Map.of("enabled", false))
                .owner("cat_owner")
                .build();
    }
}
