package com.allcli.gateway.admin;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.allcli.gateway.persistence.SystemRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.test.web.servlet.MockMvc;

/** 管理面鉴权（M4）：admin token 拦截，agent token/匿名拒绝，公共路径不受影响。 */
@SpringBootTest
@AutoConfigureMockMvc
class AdminAuthTest {

    private static final String SYSTEM_PREFIX = "m4_authsys_";
    private static final String ADMIN = "Bearer admin_m4-dev-admin";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private SystemRepository systemRepo;

    @AfterEach
    void cleanup() {
        systemRepo.findAll().stream()
                .filter(s -> s.getSystemId().startsWith(SYSTEM_PREFIX))
                .forEach(systemRepo::delete);
    }

    @Test
    void missingTokenIsDenied() throws Exception {
        mockMvc.perform(post("/api/v1/systems")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"systemId\":\"x\",\"name\":\"x\",\"baseUrl\":\"http://x\",\"authType\":\"None\",\"owner\":\"o\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("AUTH_DENIED"))
                .andExpect(jsonPath("$.category").value("auth"));
    }

    @Test
    void agentTokenIsDeniedOnAdminPath() throws Exception {
        mockMvc.perform(post("/api/v1/systems")
                        .header("Authorization", "Bearer agent_alice")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"systemId\":\"x\",\"name\":\"x\",\"baseUrl\":\"http://x\",\"authType\":\"None\",\"owner\":\"o\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("AUTH_DENIED"));
    }

    @Test
    void adminTokenPassesAndPublicPathUnaffected() throws Exception {
        // admin token 注册系统成功（顺带覆盖系统注册 happy path）
        mockMvc.perform(post("/api/v1/systems")
                        .header("Authorization", ADMIN)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"systemId\":\"" + SYSTEM_PREFIX + "ok\",\"name\":\"auth测试系统\","
                                + "\"baseUrl\":\"http://127.0.0.1:1\",\"authType\":\"None\",\"owner\":\"alice\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("OK"))
                .andExpect(jsonPath("$.data.systemId").value(SYSTEM_PREFIX + "ok"))
                .andExpect(jsonPath("$.data.status").value("active"));

        // 重复注册 → SYSTEM_ALREADY_EXISTS（uk 冲突语义）
        mockMvc.perform(post("/api/v1/systems")
                        .header("Authorization", ADMIN)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"systemId\":\"" + SYSTEM_PREFIX + "ok\",\"name\":\"again\","
                                + "\"baseUrl\":\"http://127.0.0.1:1\",\"authType\":\"None\",\"owner\":\"alice\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("SYSTEM_ALREADY_EXISTS"));

        // 公共目录检索不受拦截器影响（无 token 仍可读）
        mockMvc.perform(get("/api/v1/tools"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("OK"));
    }

    @Test
    void requiresAdminPathMatrix() {
        assertThat(AdminAuthInterceptor.requiresAdmin(req("GET", "/api/v1/audit"))).isTrue();
        assertThat(AdminAuthInterceptor.requiresAdmin(req("GET", "/api/v1/metrics"))).isTrue();
        assertThat(AdminAuthInterceptor.requiresAdmin(req("POST", "/api/v1/imports"))).isTrue();
        assertThat(AdminAuthInterceptor.requiresAdmin(req("POST", "/api/v1/credentials"))).isTrue();
        assertThat(AdminAuthInterceptor.requiresAdmin(req("POST", "/api/v1/systems"))).isTrue();
        assertThat(AdminAuthInterceptor.requiresAdmin(req("GET", "/api/v1/approvals"))).isTrue();
        assertThat(AdminAuthInterceptor.requiresAdmin(req("PUT", "/api/v1/tools/listPets"))).isTrue();
        assertThat(AdminAuthInterceptor.requiresAdmin(req("GET", "/api/v1/subscriptions"))).isTrue();
        assertThat(AdminAuthInterceptor.requiresAdmin(req("POST", "/api/v1/subscriptions/s1/revoke"))).isTrue();

        assertThat(AdminAuthInterceptor.requiresAdmin(req("GET", "/api/v1/tools"))).isFalse();
        assertThat(AdminAuthInterceptor.requiresAdmin(req("POST", "/api/v1/tools/t/publish"))).isFalse();
        assertThat(AdminAuthInterceptor.requiresAdmin(req("POST", "/api/v1/subscriptions"))).isFalse();
        assertThat(AdminAuthInterceptor.requiresAdmin(req("POST", "/api/v1/subscriptions/s1/approve"))).isFalse();
        assertThat(AdminAuthInterceptor.requiresAdmin(req("POST", "/api/v1/invoke"))).isFalse();
        assertThat(AdminAuthInterceptor.requiresAdmin(req("GET", "/actuator/prometheus"))).isFalse();
    }

    private static MockHttpServletRequest req(String method, String uri) {
        return new MockHttpServletRequest(method, uri);
    }
}
