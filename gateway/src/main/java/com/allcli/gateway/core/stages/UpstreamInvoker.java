package com.allcli.gateway.core.stages;

import com.allcli.gateway.api.dto.GatewayException;
import com.allcli.gateway.core.InvokeContext;
import com.allcli.gateway.credential.CredentialDecryptor;
import com.allcli.gateway.model.Credential;
import com.allcli.gateway.model.System;
import com.allcli.gateway.model.ToolDefinition;
import com.allcli.gateway.persistence.CredentialRepository;
import com.allcli.gateway.persistence.SystemRepository;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.Map;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.util.UriTemplate;

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
        String base = system.getBaseUrl().replaceAll("/+$", "");

        Map<String, Object> args = ctx.getRequest().getArguments() == null
                ? new LinkedHashMap<>()
                : new LinkedHashMap<>(ctx.getRequest().getArguments());

        // 路径参数展开：/pets/{petId} 从 arguments 取值（HITL 续跑同样走这里，args 来自挂起请求）
        UriTemplate template = new UriTemplate(path);
        if (!template.getVariableNames().isEmpty()) {
            Map<String, Object> pathValues = new LinkedHashMap<>();
            for (String name : template.getVariableNames()) {
                Object v = args.get(name);
                if (v == null) {
                    throw GatewayException.of("VALIDATION_ERROR", "validation",
                            "缺少路径参数: " + name + "（tool=" + tool.getToolId() + "）", false);
                }
                pathValues.put(name, v);
            }
            path = template.expand(pathValues).toString();
            args.keySet().removeAll(pathValues.keySet()); // 路径变量不再进 body/query
        }

        String url = base + path;
        boolean bodyless = method.equals("GET") || method.equals("DELETE");
        if (bodyless && !args.isEmpty()) {
            // GET/DELETE 剩余参数作为 query 转发（如 listPets 的 limit）
            StringBuilder q = new StringBuilder();
            for (Map.Entry<String, Object> e : args.entrySet()) {
                if (q.length() > 0) {
                    q.append('&');
                }
                q.append(URLEncoder.encode(e.getKey(), StandardCharsets.UTF_8)).append('=')
                        .append(URLEncoder.encode(String.valueOf(e.getValue()), StandardCharsets.UTF_8));
            }
            url = url + (url.contains("?") ? "&" : "?") + q;
        }

        HttpHeaders headers = new HttpHeaders();
        injectCredential(headers, system, tool);

        Object body = bodyless ? null : args;

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
