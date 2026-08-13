package com.allcli.gateway.credential;

import static org.assertj.core.api.Assertions.assertThat;

import com.allcli.gateway.api.dto.InvokeData;
import com.allcli.gateway.api.dto.InvokeRequest;
import com.allcli.gateway.core.InvokePipeline;
import com.allcli.gateway.model.Credential;
import com.allcli.gateway.model.Subscription;
import com.allcli.gateway.model.System;
import com.allcli.gateway.model.ToolDefinition;
import com.allcli.gateway.persistence.CredentialRepository;
import com.allcli.gateway.persistence.SubscriptionRepository;
import com.allcli.gateway.persistence.SystemRepository;
import com.allcli.gateway.persistence.ToolDefinitionRepository;
import com.sun.net.httpserver.HttpServer;
import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.util.Map;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

/**
 * 凭证信封加密端到端：明文加密成 secret_ref 落库 → invoke 注入时解密 → 下游收到正确凭证。
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE)
class CredentialEnvelopeTest {

    private static HttpServer mockServer;
    private static String mockBaseUrl;
    private static volatile String lastReceivedApiKey;

    @Autowired private InvokePipeline pipeline;
    @Autowired private EnvelopeCredentialDecryptor decryptor;
    @Autowired private ToolDefinitionRepository toolRepo;
    @Autowired private SystemRepository systemRepo;
    @Autowired private CredentialRepository credRepo;
    @Autowired private SubscriptionRepository subRepo;
    @Autowired private com.allcli.gateway.persistence.InvocationRepository invRepo;

    private static final String SYSTEM_ID = "gw-cred-system";
    private static final String CRED_ID = "cred-test";
    private static final String TOOL_ID = "gw_cred_list";

    @BeforeAll
    static void startMock() throws IOException {
        mockServer = HttpServer.create(new InetSocketAddress(0), 0);
        mockServer.createContext("/data", exchange -> {
            lastReceivedApiKey = exchange.getRequestHeaders().getFirst("X-API-Key");
            byte[] resp = "{\"ok\":true}".getBytes();
            exchange.getResponseHeaders().set("Content-Type", "application/json;charset=utf-8");
            exchange.sendResponseHeaders(200, resp.length);
            try (OutputStream os = exchange.getResponseBody()) {
                os.write(resp);
            }
        });
        mockServer.start();
        mockBaseUrl = "http://127.0.0.1:" + mockServer.getAddress().getPort();
    }

    @AfterAll
    static void stopMock() {
        if (mockServer != null) {
            mockServer.stop(0);
        }
    }

    @AfterEach
    void cleanup() {
        invRepo.deleteAll(invRepo.findAll());
        subRepo.deleteAll(subRepo.findAll());
        credRepo.findAll().stream().filter(c -> CRED_ID.equals(c.getCredId())).forEach(credRepo::delete);
        toolRepo.findAll().stream().filter(t -> TOOL_ID.equals(t.getToolId())).forEach(toolRepo::delete);
        systemRepo.findAll().stream().filter(s -> SYSTEM_ID.equals(s.getSystemId())).forEach(systemRepo::delete);
        lastReceivedApiKey = null;
    }

    @Test
    void encryptedCredentialIsDecryptedAndInjected() {
        // 明文凭证 → 信封加密 → 存 secret_ref（明文不入库）
        String plaintext = "AllCLI-real-api-key-12345";
        String secretRef = decryptor.encrypt(plaintext);
        assertThat(secretRef).startsWith("allcli-vault://dek/");
        assertThat(secretRef).doesNotContain(plaintext);

        systemRepo.save(System.builder()
                .systemId(SYSTEM_ID).name("cred").baseUrl(mockBaseUrl)
                .authType("ApiKey").owner("alice").status("active").build());
        credRepo.save(Credential.builder()
                .credId(CRED_ID).systemRef(SYSTEM_ID).type("Vault")
                .secretRef(secretRef).status("active").build());
        toolRepo.save(ToolDefinition.builder()
                .toolId(TOOL_ID).name(TOOL_ID).version("1.0.0").status("published")
                .systemRef(SYSTEM_ID).credentialRef(CRED_ID).description("cred tool")
                .endpoint(Map.of("method", "GET", "path", "/data", "timeoutMs", 30000))
                .inputSchema(Map.of("type", "object"))
                .sideEffect("readonly").reversibility("readonly")
                .owner("alice").build());
        subRepo.save(Subscription.builder()
                .subId("sub-cred").agentId("agent-cred").toolId(TOOL_ID).status("approved")
                .approvedBy("alice").build());

        InvokeData data = pipeline.execute("trace-cred", "agent-cred", InvokeRequest.builder()
                .toolName(TOOL_ID).arguments(Map.of()).build());

        assertThat(data.getStatus()).isEqualTo("succeeded");
        // 下游收到了正确解密后的明文凭证（Agent 永不接触明文）
        assertThat(lastReceivedApiKey).isEqualTo(plaintext);
    }
}
