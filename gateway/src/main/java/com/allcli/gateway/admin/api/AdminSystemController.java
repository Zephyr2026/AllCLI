package com.allcli.gateway.admin.api;

import com.allcli.gateway.admin.api.dto.SystemRegisterRequest;
import com.allcli.gateway.admin.api.dto.SystemResponse;
import com.allcli.gateway.admin.core.SystemAdminService;
import com.allcli.gateway.api.dto.ApiResponse;
import com.allcli.gateway.api.dto.GatewayException;
import com.allcli.gateway.core.GatewayUtil;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 存量系统注册（admin 面，FR-CONV 前置）。鉴权由 AdminAuthInterceptor 拦截
 * （Bearer admin_xxx），controller 内不重复校验。
 */
@RestController
@RequestMapping("/api/v1/systems")
public class AdminSystemController {

    private final SystemAdminService systemAdminService;

    public AdminSystemController(SystemAdminService systemAdminService) {
        this.systemAdminService = systemAdminService;
    }

    /** 注册存量系统（幂等冲突返回 SYSTEM_ALREADY_EXISTS）。 */
    @PostMapping
    public ApiResponse create(@RequestBody SystemRegisterRequest req) {
        String traceId = GatewayUtil.id("trace");
        try {
            return ApiResponse.ok(systemAdminService.register(req), traceId);
        } catch (GatewayException e) {
            return ApiResponse.error(e.getCode(), e.getCategory(), e.getMessage(), e.isRetriable(), traceId);
        }
    }
}
