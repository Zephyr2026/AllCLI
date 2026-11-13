package com.allcli.gateway.admin;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.allcli.gateway.credential.EnvelopeCredentialDecryptor;
import com.allcli.gateway.model.System;
import com.allcli.gateway.persistence.CredentialRepository;
import com.allcli.gateway.persistence.SystemRepository;
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

/** 凭证注册 API（FR-CONV-04）：明文信封加密落库，可解密回读，响应不回显。 */
@SpringBootTest
@AutoConfigureMockMvc
class AdminCredentialTest {

    private static final String SYSTEM_ID = "m4_credsys_1";
    private static final String ADMIN = "Bearer admin_m4-dev-admin";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private SystemRepository systemRepo;

    @Autowired
    private CredentialRepository credRepo;

    @Autowired
    private EnvelopeCredentialDecryptor decryptor;

    @BeforeEach
    void seed() {
        systemRepo.save(System.builder()
                .systemId(SYSTEM_ID).name("凭证测试系统").baseUrl("http://127.0.0.1:1")
                .authType("ApiKey").owner("alice").status("active").extra(Map.of())
                .build());
    }

    @AfterEach
    void cleanup() {
        credRepo.findBySystemRef(SYSTEM_ID).ifPresent(credRepo::delete);
        systemRepo.findBySystemId(SYSTEM_ID).ifPresent(systemRepo::delete);
    }

    @Test
    void registerEncryptsAndRoundTrips() throws Exception {
        MvcResult result = mockMvc.perform(post("/api/v1/credentials")
                        .header("Authorization", ADMIN)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"systemRef\":\"" + SYSTEM_ID + "\",\"type\":\"Vault\","
                                + "\"secret\":\"sk-test-12345\",\"rotatePolicy\":\"30d\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("OK"))
                .andExpect(jsonPath("$.data.credId").exists())
                .andExpect(jsonPath("$.data.status").value("active"))
                .andReturn();

        // 响应体不回显明文与 secret_ref
        String body = result.getResponse().getContentAsString();
        assertThat(body).doesNotContain("sk-test-12345").doesNotContain("secretRef").doesNotContain("allcli-vault");

        // DB：secret_ref 为信封格式且不含明文；解密回读一致（即网关转发时可用）
        var saved = credRepo.findBySystemRef(SYSTEM_ID).orElseThrow();
        assertThat(saved.getSecretRef()).startsWith("allcli-vault://dek/");
        assertThat(saved.getSecretRef()).doesNotContain("sk-test-12345");
        assertThat(decryptor.decrypt(saved.getSecretRef())).isEqualTo("sk-test-12345");
    }

    @Test
    void unknownSystemRejected() throws Exception {
        mockMvc.perform(post("/api/v1/credentials")
                        .header("Authorization", ADMIN)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"systemRef\":\"m4_credsys_none\",\"type\":\"Vault\",\"secret\":\"s\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("SYSTEM_NOT_FOUND"));
    }
}
