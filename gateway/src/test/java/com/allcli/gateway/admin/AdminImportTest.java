package com.allcli.gateway.admin;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.allcli.gateway.model.System;
import com.allcli.gateway.persistence.SystemRepository;
import com.allcli.gateway.persistence.ToolDefinitionRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

/** 导入 REST 化（FR-CONV-01/02/03，admin 面）：异步 job + 冲突不覆盖 + 推断落库。 */
@SpringBootTest
@AutoConfigureMockMvc
class AdminImportTest {

    private static final String SYSTEM_ID = "m4_impsys_1";
    private static final String TOOL_PREFIX = "m4_imp_";
    private static final String ADMIN = "Bearer admin_m4-dev-admin";

    /** 与 petstore 同构的最小 spec：GET=readonly、POST=reversible+HITL、DELETE=irreversible+HITL。 */
    private static final String SPEC = """
            openapi: 3.0.0
            info:
              title: m4 import api
              version: 1.0.0
            paths:
              /pets:
                get:
                  operationId: m4_imp_list
                  summary: 列出宠物
                  responses:
                    '200':
                      description: ok
                post:
                  operationId: m4_imp_create
                  summary: 创建宠物
                  requestBody:
                    content:
                      application/json:
                        schema:
                          type: object
                          properties:
                            name:
                              type: string
                  responses:
                    '201':
                      description: created
              /pets/{petId}:
                delete:
                  operationId: m4_imp_delete
                  summary: 删除宠物
                  parameters:
                    - name: petId
                      in: path
                      required: true
                      schema:
                        type: integer
                  responses:
                    '204':
                      description: deleted
            """;

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private SystemRepository systemRepo;

    @Autowired
    private ToolDefinitionRepository toolRepo;

    private final ObjectMapper mapper = new ObjectMapper();

    @BeforeEach
    void seed() {
        systemRepo.save(System.builder()
                .systemId(SYSTEM_ID).name("导入测试系统").baseUrl("http://127.0.0.1:1")
                .authType("None").owner("alice").status("active").extra(Map.of())
                .build());
    }

    @AfterEach
    void cleanup() {
        toolRepo.findAll().stream()
                .filter(t -> t.getToolId().startsWith(TOOL_PREFIX))
                .forEach(toolRepo::delete);
        systemRepo.findBySystemId(SYSTEM_ID).ifPresent(systemRepo::delete);
    }

    @Test
    void importCreatesDraftToolsWithInference() throws Exception {
        String jobId = submitImport();
        Map<String, Object> job = awaitDone(jobId);

        assertThat(strings(job, "created")).containsExactlyInAnyOrder(
                "m4_imp_list", "m4_imp_create", "m4_imp_delete");
        assertThat(strings(job, "conflicts")).isEmpty();

        var list = toolRepo.findByToolId("m4_imp_list").orElseThrow();
        assertThat(list.getStatus()).isEqualTo("draft");
        assertThat(list.getSideEffect()).isEqualTo("readonly");
        assertThat(list.getHitl()).isNull();

        var create = toolRepo.findByToolId("m4_imp_create").orElseThrow();
        assertThat(create.getSideEffect()).isEqualTo("write");
        assertThat(create.getReversibility()).isEqualTo("reversible");
        assertThat(create.getHitl()).containsEntry("enabled", true);

        var delete = toolRepo.findByToolId("m4_imp_delete").orElseThrow();
        assertThat(delete.getReversibility()).isEqualTo("irreversible");
    }

    @Test
    void reimportConflictsWithoutOverwrite() throws Exception {
        submitAndAwait();
        // 篡改 description 后重导：冲突项不得被覆盖
        var list = toolRepo.findByToolId("m4_imp_list").orElseThrow();
        list.setDescription("手工改过的描述");
        toolRepo.save(list);

        Map<String, Object> job = awaitDone(submitImport());
        assertThat(strings(job, "created")).isEmpty();
        assertThat(strings(job, "conflicts")).containsExactlyInAnyOrder(
                "m4_imp_list", "m4_imp_create", "m4_imp_delete");
        assertThat(toolRepo.findByToolId("m4_imp_list").orElseThrow().getDescription())
                .isEqualTo("手工改过的描述");
    }

    @Test
    void unknownSystemRejectedAtSubmit() throws Exception {
        mockMvc.perform(post("/api/v1/imports")
                        .header("Authorization", ADMIN)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body("m4_impsys_none")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("SYSTEM_NOT_FOUND"));

        // sourceUrl 与 sourceContent 同时缺失/同时给也同步拒绝
        mockMvc.perform(post("/api/v1/imports")
                        .header("Authorization", ADMIN)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(mapper.writeValueAsString(Map.of("systemRef", SYSTEM_ID))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));
    }

    private String body(String systemRef) throws Exception {
        return mapper.writeValueAsString(Map.of(
                "sourceType", "openapi",
                "sourceContent", SPEC,
                "systemRef", systemRef));
    }

    private String submitImport() throws Exception {
        MvcResult result = mockMvc.perform(post("/api/v1/imports")
                        .header("Authorization", ADMIN)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body(SYSTEM_ID).getBytes(StandardCharsets.UTF_8)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("OK"))
                .andExpect(jsonPath("$.data.jobId").exists())
                .andExpect(jsonPath("$.data.status").value("running"))
                .andReturn();
        return mapper.readTree(result.getResponse()
                .getContentAsString(StandardCharsets.UTF_8)).path("data").path("jobId").asText();
    }

    private void submitAndAwait() throws Exception {
        awaitDone(submitImport());
    }

    /** 轮询至 done/failed（上限 15s），返回 data 节点。 */
    private Map<String, Object> awaitDone(String jobId) throws Exception {
        long deadline = java.lang.System.currentTimeMillis() + 15_000;
        while (java.lang.System.currentTimeMillis() < deadline) {
            MvcResult result = mockMvc.perform(get("/api/v1/imports/" + jobId)
                            .header("Authorization", ADMIN))
                    .andExpect(status().isOk())
                    .andReturn();
            var node = mapper.readTree(result.getResponse()
                    .getContentAsString(StandardCharsets.UTF_8)).path("data");
            if (!"running".equals(node.path("status").asText())) {
                assertThat(node.path("status").asText()).isEqualTo("done");
                @SuppressWarnings("unchecked")
                Map<String, Object> data = mapper.convertValue(node, Map.class);
                return data;
            }
            Thread.sleep(200);
        }
        throw new AssertionError("导入 job 超时未完成: " + jobId);
    }

    @SuppressWarnings("unchecked")
    private static java.util.List<String> strings(Map<String, Object> job, String key) {
        Object v = job.get(key);
        return v == null ? java.util.List.of() : (java.util.List<String>) v;
    }
}
