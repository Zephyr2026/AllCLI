package com.allcli.gateway.admin.api;

import com.allcli.gateway.admin.api.dto.CredentialRegisterRequest;
import com.allcli.gateway.admin.api.dto.CredentialResponse;
import com.allcli.gateway.admin.core.CredentialAdminService;
import com.allcli.gateway.api.dto.ApiResponse;
import com.allcli.gateway.api.dto.GatewayException;
import com.allcli.gateway.core.GatewayUtil;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 凭证注册（admin 面，FR-CONV-04）：明文信封加密后只存 secret_ref。
 * 鉴权由 AdminAuthInterceptor 拦截（Bearer admin_xxx）。
 */
@RestController
@RequestMapping("/api/v1/credentials")
public class AdminCredentialController {

    private final CredentialAdminService credentialAdminService;

    public AdminCredentialController(CredentialAdminService credentialAdminService) {
        this.credentialAdminService = credentialAdminService;
    }

    /** 注册凭证（响应不含 secret 与 secret_ref）。 */
    @PostMapping
    public ApiResponse create(@RequestBody CredentialRegisterRequest req) {
        String traceId = GatewayUtil.id("trace");
        try {
            return ApiResponse.ok(credentialAdminService.register(req), traceId);
        } catch (GatewayException e) {
            return ApiResponse.error(e.getCode(), e.getCategory(), e.getMessage(), e.isRetriable(), traceId);
        }
    }
}
