package com.allcli.gateway.core.stages;

import com.allcli.gateway.api.dto.GatewayException;
import com.allcli.gateway.core.InvokeContext;
import com.allcli.gateway.credential.CredentialDecryptor;
import com.allcli.gateway.model.Credential;
import com.allcli.gateway.model.System;
import com.allcli.gateway.model.ToolDefinition;
import com.allcli.gateway.persistence.CredentialRepository;
import com.allcli.gateway.persistence.SystemRepository;
import java.util.Map;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;

/**
 * Step 7：转发调用存量系统。
 *
 * <p>取 System.base_url + endpoint(method,path) + 凭证 → 注入 Header → 调用下游。
 * 凭证按 auth_type 注入：ApiKey→X-API-Key，OAuth2/Bearer→Authorization，Cookie→Cookie，None→不注入。
 * 超时/异常 → UPSTREAM_ERROR(retriable)。
 */
@Component
public class UpstreamInvoker {

    private final SystemRepository systemRepo;
    private final CredentialRepository credRepo;
    private final CredentialDecryptor decryptor;
    private final RestTemplate restTemplate;

    @Autowired
    public UpstreamInvoker(SystemRepository systemRepo, CredentialRepository credRepo,
                           CredentialDecryptor decryptor) {
        this.systemRepo = systemRepo;
        this.credRepo = credRepo;
        this.decryptor = decryptor;
        this.restTemplate = new RestTemplate();
    }

    public Object invoke(InvokeContext ctx) {
        ToolDefinition tool = ctx.getTool();
        System system = systemRepo.findBySystemId(tool.getSystemRef()).orElseThrow(() ->
                GatewayException.of("UPSTREAM_ERROR", "upstream",
                        "存量系统未配置: " + tool.getSystemRef(), false));

        Map<String, Object> endpoint = tool.getEndpoint();
        String method = String.valueOf(endpoint.get("method")).toUpperCase();
        String path = String.valueOf(endpoint.get("path"));
        String url = system.getBaseUrl().replaceAll("/+$", "") + path;

        HttpHeaders headers = new HttpHeaders();
        injectCredential(headers, system, tool);

        Object body = method.equals("GET") || method.equals("DELETE")
                ? null
                : ctx.getRequest().getArguments();

        try {
            ResponseEntity<Object> resp = restTemplate.exchange(
                    url, HttpMethod.valueOf(method),
                    new HttpEntity<>(body, headers), Object.class);
            return resp.getBody();
        } catch (RestClientException e) {
            throw GatewayException.of("UPSTREAM_ERROR", "upstream",
                    "存量系统调用失败: " + e.getMessage(), true);
        }
    }

    private void injectCredential(HttpHeaders headers, System system, ToolDefinition tool) {
        if (tool.getCredentialRef() == null || "None".equalsIgnoreCase(system.getAuthType())) {
            return;
        }
        Credential cred = credRepo.findByCredId(tool.getCredentialRef()).orElse(null);
        if (cred == null) {
            return;
        }
        String plaintext = decryptor.decrypt(cred.getSecretRef());
        switch (system.getAuthType()) {
            case "ApiKey" -> headers.set("X-API-Key", plaintext);
            case "OAuth2", "Bearer" -> headers.setBearerAuth(plaintext);
            case "Cookie" -> headers.set("Cookie", plaintext);
            default -> { /* None 或未知：不注入 */ }
        }
    }
}
