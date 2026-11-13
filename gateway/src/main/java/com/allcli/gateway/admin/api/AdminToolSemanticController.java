package com.allcli.gateway.admin.api;

import com.allcli.gateway.admin.api.dto.ToolSemanticUpdateRequest;
import com.allcli.gateway.admin.api.dto.ToolSemanticUpdateResponse;
import com.allcli.gateway.admin.core.ToolSemanticService;
import com.allcli.gateway.api.dto.ApiResponse;
import com.allcli.gateway.api.dto.GatewayException;
import com.allcli.gateway.core.GatewayUtil;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RestController;

/**
 * 工具语义编辑（admin 面，FR-SEM-01~04）：PUT /api/v1/tools/{toolId}。
 * 鉴权由 AdminAuthInterceptor 拦截（PUT /api/v1/tools/** = admin）。
 */
@RestController
public class AdminToolSemanticController {

    private final ToolSemanticService toolSemanticService;

    public AdminToolSemanticController(ToolSemanticService toolSemanticService) {
        this.toolSemanticService = toolSemanticService;
    }

    /** 语义字段编辑（非 null 合并覆盖；published 见回退/热生效规则，响应含 warnings）。 */
    @PutMapping("/api/v1/tools/{toolId}")
    public ApiResponse update(@PathVariable String toolId,
                              @RequestBody ToolSemanticUpdateRequest req,
                              @RequestHeader(value = "X-Trace-Id", required = false) String traceId) {
        traceId = (traceId == null || traceId.isEmpty()) ? GatewayUtil.id("trace") : traceId;
        try {
            return ApiResponse.ok(toolSemanticService.apply(toolId, req), traceId);
        } catch (GatewayException e) {
            return ApiResponse.error(e.getCode(), e.getCategory(), e.getMessage(), e.isRetriable(), traceId);
        }
    }
}
