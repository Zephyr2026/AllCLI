package com.allcli.gateway.catalog;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.allcli.gateway.model.ToolDefinition;
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
import org.springframework.test.web.servlet.MvcResult;

/** 目录只读 API（FR-REG-02）：检索过滤/分页/脱敏。 */
@SpringBootTest
@AutoConfigureMockMvc
class ToolCatalogControllerTest {

    private static final String PREFIX = "cat_t_";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ToolDefinitionRepository toolRepo;

    @BeforeEach
    void seed() {
        toolRepo.save(tool("cat_t_a", "published", "sysA", "readonly", "readonly", "查询宠物列表"));
        ToolDefinition b = tool("cat_t_b", "draft", "sysA", "write", "irreversible", "删除宠物");
        toolRepo.save(b);
        ToolDefinition c = tool("cat_t_c", "published", "sysB", "write", "reversible", "创建宠物");
        c.setCredentialRef("cred_secret");
        c.setEndpoint(Map.of("method", "POST", "path", "/pets"));
        toolRepo.save(c);
    }

    @AfterEach
    void cleanup() {
        toolRepo.findAll().stream()
                .filter(t -> t.getToolId().startsWith(PREFIX))
                .forEach(toolRepo::delete);
    }

    @Test
    void listReturnsPaginatedSummaries() throws Exception {
        // q 限定自身种子，不依赖库为空（DB 可能留有 E2E 演示数据）
        mockMvc.perform(get("/api/v1/tools").param("q", PREFIX))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("OK"))
                .andExpect(jsonPath("$.data.total").value(3))
                .andExpect(jsonPath("$.data.items[0].toolId").exists())
                .andExpect(jsonPath("$.data.items[0].inputSchema").doesNotExist());
    }

    @Test
    void filterBySystemAndStatus() throws Exception {
        mockMvc.perform(get("/api/v1/tools").param("system", "sysA").param("status", "published"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.total").value(1))
                .andExpect(jsonPath("$.data.items[0].toolId").value("cat_t_a"));
    }

    @Test
    void filterByReversibility() throws Exception {
        mockMvc.perform(get("/api/v1/tools")
                        .param("q", PREFIX).param("reversibility", "irreversible"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.total").value(1))
                .andExpect(jsonPath("$.data.items[0].toolId").value("cat_t_b"));
    }

    @Test
    void searchByKeyword() throws Exception {
        MvcResult r = mockMvc.perform(get("/api/v1/tools").param("q", "cat_t_b"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.total").value(1))
                .andExpect(jsonPath("$.data.items[0].toolId").value("cat_t_b"))
                .andReturn();
        assertThat(r.getResponse().getContentAsString()).contains("删除宠物");
    }

    @Test
    void paginationSlice() throws Exception {
        mockMvc.perform(get("/api/v1/tools")
                        .param("q", PREFIX).param("page", "0").param("size", "2"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.pageSize").value(2))
                .andExpect(jsonPath("$.data.total").value(3))
                .andExpect(jsonPath("$.data.page").value(0));
    }

    @Test
    void detailIsSanitizedButComplete() throws Exception {
        MvcResult r = mockMvc.perform(get("/api/v1/tools/cat_t_c"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("OK"))
                .andExpect(jsonPath("$.data.toolId").value("cat_t_c"))
                .andExpect(jsonPath("$.data.inputSchema.type").value("object"))
                .andExpect(jsonPath("$.data.hitlEnabled").value(true))
                .andReturn();
        // 脱敏：上游地址与凭证引用不得出现在目录响应
        String body = r.getResponse().getContentAsString();
        assertThat(body).doesNotContain("cred_secret").doesNotContain("/pets");
    }

    @Test
    void detailNotFound() throws Exception {
        mockMvc.perform(get("/api/v1/tools/cat_t_missing"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("TOOL_NOT_FOUND"))
                .andExpect(jsonPath("$.category").value("validation"));
    }

    private ToolDefinition tool(String toolId, String status, String system,
                                String sideEffect, String reversibility, String description) {
        return ToolDefinition.builder()
                .toolId(toolId)
                .name(toolId)
                .version("1.0.0")
                .status(status)
                .systemRef(system)
                .description(description)
                .endpoint(Map.of("method", "GET", "path", "/" + toolId, "timeoutMs", 3000))
                .inputSchema(Map.of("type", "object",
                        "properties", Map.of("name", Map.of("type", "string")),
                        "required", List.of("name")))
                .sideEffect(sideEffect)
                .reversibility(reversibility)
                .hitl(Map.of("enabled", true, "approverType", "human",
                        "approverRef", "cat_owner", "timeoutSec", 300))
                .owner("cat_owner")
                .build();
    }
}
