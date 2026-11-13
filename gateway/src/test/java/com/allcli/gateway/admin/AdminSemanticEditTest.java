package com.allcli.gateway.admin;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.allcli.gateway.catalog.event.ToolCatalogChangedEvent;
import com.allcli.gateway.model.ToolDefinition;
import com.allcli.gateway.persistence.ToolDefinitionRepository;
import java.util.Map;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.event.ApplicationEvents;
import org.springframework.test.context.event.RecordApplicationEvents;
import org.springframework.test.web.servlet.MockMvc;

/** 语义编辑（FR-SEM-01~04，admin 面）：draft 直改、published 热更/回退、告警与事件。 */
@SpringBootTest
@AutoConfigureMockMvc
@RecordApplicationEvents
class AdminSemanticEditTest {

    private static final String PREFIX = "m4_semt_";
    private static final String ADMIN = "Bearer admin_m4-dev-admin";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ToolDefinitionRepository toolRepo;

    @Autowired
    private ApplicationEvents events;

    @BeforeEach
    void seed() {
        toolRepo.save(tool(PREFIX + "draft", "draft"));
        toolRepo.save(tool(PREFIX + "pub", "published"));
        toolRepo.save(tool(PREFIX + "dep", "deprecated"));
    }

    @AfterEach
    void cleanup() {
        toolRepo.findAll().stream()
                .filter(t -> t.getToolId().startsWith(PREFIX))
                .forEach(toolRepo::delete);
    }

    @Test
    void draftEditAppliesFieldsWithoutEvent() throws Exception {
        mockMvc.perform(put("/api/v1/tools/" + PREFIX + "draft")
                        .header("Authorization", ADMIN)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"sideEffect\":\"write\",\"reversibility\":\"reversible\","
                                + "\"description\":\"创建宠物（改）\","
                                + "\"hitl\":{\"enabled\":true,\"approverRef\":\"agent_owner\",\"timeoutSec\":300}}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("OK"))
                .andExpect(jsonPath("$.data.status").value("draft"))
                .andExpect(jsonPath("$.data.version").value("1.0.0"))
                .andExpect(jsonPath("$.data.warnings").isEmpty());

        ToolDefinition t = toolRepo.findByToolId(PREFIX + "draft").orElseThrow();
        assertThat(t.getDescription()).isEqualTo("创建宠物（改）");
        assertThat(t.getSideEffect()).isEqualTo("write");
        assertThat(t.getHitl()).containsEntry("enabled", true).containsEntry("approverRef", "agent_owner");
        assertThat(events.stream(ToolCatalogChangedEvent.class)
                .filter(e -> e.toolId().startsWith(PREFIX)).count()).isZero();
    }

    @Test
    void draftWriteMissingReversibilityWarnsButSaves() throws Exception {
        mockMvc.perform(put("/api/v1/tools/" + PREFIX + "draft")
                        .header("Authorization", ADMIN)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"sideEffect\":\"write\",\"reversibility\":\"readonly\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("OK"))
                .andExpect(jsonPath("$.data.warnings[0]").value("WRITE_MISSING_REVERSIBILITY"));
        assertThat(toolRepo.findByToolId(PREFIX + "draft").orElseThrow().getSideEffect()).isEqualTo("write");
    }

    @Test
    void publishedDescriptionOnlyHotUpdates() throws Exception {
        mockMvc.perform(put("/api/v1/tools/" + PREFIX + "pub")
                        .header("Authorization", ADMIN)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"description\":\"仅改描述\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("published"))
                .andExpect(jsonPath("$.data.version").value("1.0.0"))
                .andExpect(jsonPath("$.data.warnings").isEmpty());

        assertThat(events.stream(ToolCatalogChangedEvent.class)
                .filter(e -> (PREFIX + "pub").equals(e.toolId())
                        && ToolCatalogChangedEvent.ACTION_UPDATED.equals(e.action()))
                .count()).isGreaterThanOrEqualTo(1);
    }

    @Test
    void publishedGovernanceChangeRevertsToDraftWithVersionBump() throws Exception {
        mockMvc.perform(put("/api/v1/tools/" + PREFIX + "pub")
                        .header("Authorization", ADMIN)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"sideEffect\":\"write\",\"reversibility\":\"reversible\","
                                + "\"hitl\":{\"enabled\":true,\"approverRef\":\"agent_owner\"}}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("draft"))
                .andExpect(jsonPath("$.data.version").value("1.0.1"))
                .andExpect(jsonPath("$.data.warnings[0]").value("TOOL_REVERTED_TO_DRAFT"));

        assertThat(events.stream(ToolCatalogChangedEvent.class)
                .filter(e -> (PREFIX + "pub").equals(e.toolId())
                        && ToolCatalogChangedEvent.ACTION_DEPRECATED.equals(e.action()))
                .count()).isGreaterThanOrEqualTo(1);
    }

    @Test
    void deprecatedAndInvalidInputsRejected() throws Exception {
        mockMvc.perform(put("/api/v1/tools/" + PREFIX + "dep")
                        .header("Authorization", ADMIN)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"description\":\"x\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("INVALID_STATUS_TRANSITION"));

        mockMvc.perform(put("/api/v1/tools/" + PREFIX + "draft")
                        .header("Authorization", ADMIN)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"sideEffect\":\"delete\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));

        mockMvc.perform(put("/api/v1/tools/" + PREFIX + "draft")
                        .header("Authorization", ADMIN)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"hitl\":{\"enabled\":true}}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));

        mockMvc.perform(put("/api/v1/tools/m4_semt_none")
                        .header("Authorization", ADMIN)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"description\":\"x\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("TOOL_NOT_FOUND"));
    }

    private static ToolDefinition tool(String toolId, String status) {
        return ToolDefinition.builder()
                .toolId(toolId).name(toolId).version("1.0.0").systemRef("m4_semsys")
                .description("语义测试工具").sideEffect("readonly").reversibility("readonly")
                .endpoint(Map.of("method", "GET", "path", "/x"))
                .status(status).owner("alice").build();
    }
}
