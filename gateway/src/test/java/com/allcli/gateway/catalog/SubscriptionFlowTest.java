package com.allcli.gateway.catalog;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.allcli.gateway.model.Subscription;
import com.allcli.gateway.model.ToolDefinition;
import com.allcli.gateway.persistence.SubscriptionRepository;
import com.allcli.gateway.persistence.ToolDefinitionRepository;
import java.util.Map;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.web.servlet.MockMvc;

/** 订阅流转（FR-REG-03）：create→pending、approve/reject、重复/非法流转。 */
@SpringBootTest
@AutoConfigureMockMvc
class SubscriptionFlowTest {

    private static final String PREFIX = "cat_s_";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private SubscriptionRepository subRepo;

    @Autowired
    private ToolDefinitionRepository toolRepo;

    @BeforeEach
    void seed() {
        ToolDefinition tool = ToolDefinition.builder()
                .toolId("cat_s_tool").name("cat_s_tool").version("1.0.0")
                .status("published").systemRef("sysA").description("订阅测试")
                .endpoint(Map.of("method", "GET", "path", "/x", "timeoutMs", 3000))
                .sideEffect("readonly").reversibility("readonly")
                .hitl(Map.of("enabled", false)).owner("cat_owner")
                .build();
        toolRepo.save(tool);
    }

    @AfterEach
    void cleanup() {
        subRepo.findAll().stream()
                .filter(s -> s.getToolId().startsWith(PREFIX))
                .forEach(subRepo::delete);
        toolRepo.findAll().stream()
                .filter(t -> t.getToolId().startsWith(PREFIX))
                .forEach(toolRepo::delete);
    }

    @Test
    void createThenApprove() throws Exception {
        String subId = mockMvc.perform(post("/api/v1/subscriptions")
                        .contentType("application/json")
                        .content("{\"agentId\":\"cat_s_agent\",\"toolId\":\"cat_s_tool\",\"scope\":\"read\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("pending"))
                .andExpect(jsonPath("$.data.subId").exists())
                .andReturn().getResponse().getContentAsString()
                .replaceAll(".*\"subId\":\"([^\"]+)\".*", "$1");

        mockMvc.perform(post("/api/v1/subscriptions/{subId}/approve", subId)
                        .header("Authorization", "Bearer agent_owner"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("approved"))
                .andExpect(jsonPath("$.data.approvedBy").value("owner"));

        Subscription sub = subRepo.findBySubId(subId).orElseThrow();
        assertThat(sub.getStatus()).isEqualTo("approved");
    }

    @Test
    void createThenReject() throws Exception {
        String subId = createSub("cat_s_agent2");
        mockMvc.perform(post("/api/v1/subscriptions/{subId}/reject", subId)
                        .header("Authorization", "Bearer agent_owner"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("rejected"));
    }

    @Test
    void duplicateSubscriptionRejected() throws Exception {
        createSub("cat_s_agent3");
        mockMvc.perform(post("/api/v1/subscriptions")
                        .contentType("application/json")
                        .content("{\"agentId\":\"cat_s_agent3\",\"toolId\":\"cat_s_tool\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("SUB_ALREADY_EXISTS"));
    }

    @Test
    void unknownToolRejected() throws Exception {
        mockMvc.perform(post("/api/v1/subscriptions")
                        .contentType("application/json")
                        .content("{\"agentId\":\"cat_s_agent4\",\"toolId\":\"cat_s_nope\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("TOOL_NOT_FOUND"));
    }

    @Test
    void decideTwiceRejected() throws Exception {
        String subId = createSub("cat_s_agent5");
        mockMvc.perform(post("/api/v1/subscriptions/{subId}/approve", subId)
                        .header("Authorization", "Bearer agent_owner"))
                .andExpect(status().isOk());
        mockMvc.perform(post("/api/v1/subscriptions/{subId}/approve", subId)
                        .header("Authorization", "Bearer agent_owner"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("INVALID_STATUS_TRANSITION"));
    }

    @Test
    void decideRequiresToken() throws Exception {
        String subId = createSub("cat_s_agent6");
        mockMvc.perform(post("/api/v1/subscriptions/{subId}/approve", subId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("AUTH_DENIED"));
    }

    private String createSub(String agentId) throws Exception {
        return mockMvc.perform(post("/api/v1/subscriptions")
                        .contentType("application/json")
                        .content("{\"agentId\":\"" + agentId + "\",\"toolId\":\"cat_s_tool\"}"))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString()
                .replaceAll(".*\"subId\":\"([^\"]+)\".*", "$1");
    }
}
