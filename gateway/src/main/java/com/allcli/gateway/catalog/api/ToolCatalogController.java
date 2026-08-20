package com.allcli.gateway.catalog.api;

import com.allcli.gateway.api.dto.ApiResponse;
import com.allcli.gateway.api.dto.GatewayException;
import com.allcli.gateway.catalog.api.dto.PageResponse;
import com.allcli.gateway.catalog.api.dto.PublishRequest;
import com.allcli.gateway.catalog.api.dto.PublishResponse;
import com.allcli.gateway.catalog.api.dto.ToolDetailResponse;
import com.allcli.gateway.catalog.api.dto.ToolSummaryResponse;
import com.allcli.gateway.catalog.core.CatalogService;
import com.allcli.gateway.core.GatewayUtil;
import com.allcli.gateway.core.stages.AuthService;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * L3 目录：工具检索/详情（FR-REG-02，读公开）+ 发布/下线（FR-REG-01，写需 agent token）。
 * 一期无 RBAC：任意合法 agent token 可作为操作人（见 M3 风险清单）。
 */
@RestController
@RequestMapping("/api/v1/tools")
public class ToolCatalogController {

    private final CatalogService catalogService;
    private final AuthService authService;

    public ToolCatalogController(CatalogService catalogService, AuthService authService) {
        this.catalogService = catalogService;
        this.authService = authService;
    }

    /** 检索：q（toolId/name/description 模糊）+ system/reversibility/status 过滤 + 分页。 */
    @GetMapping
    public ApiResponse list(@RequestParam(required = false) String q,
                            @RequestParam(required = false) String system,
                            @RequestParam(required = false) String reversibility,
                            @RequestParam(required = false) String status,
                            @PageableDefault(size = 20, sort = "toolId") Pageable pageable,
                            @RequestHeader(value = "X-Trace-Id", required = false) String traceId) {
        traceId = traceId(traceId);
        try {
            Page<ToolSummaryResponse> page =
                    catalogService.list(q, system, reversibility, status, pageable);
            return ApiResponse.ok(PageResponse.of(page), traceId);
        } catch (GatewayException e) {
            return ApiResponse.error(e.getCode(), e.getCategory(), e.getMessage(), e.isRetriable(), traceId);
        }
    }

    /** 详情：脱敏后的完整定义（含 inputSchema/HITL/示例）。 */
    @GetMapping("/{toolId}")
    public ApiResponse detail(@PathVariable String toolId,
                              @RequestHeader(value = "X-Trace-Id", required = false) String traceId) {
        traceId = traceId(traceId);
        try {
            ToolDetailResponse detail = catalogService.detail(toolId);
            return ApiResponse.ok(detail, traceId);
        } catch (GatewayException e) {
            return ApiResponse.error(e.getCode(), e.getCategory(), e.getMessage(), e.isRetriable(), traceId);
        }
    }

    /** 发布：draft→published/gray，写版本快照；MISSING_REVERSIBILITY 门禁。 */
    @PostMapping("/{toolId}/publish")
    public ApiResponse publish(@PathVariable String toolId,
                               @RequestHeader(value = "Authorization", required = false) String authorization,
                               @RequestHeader(value = "X-Trace-Id", required = false) String traceId,
                               @RequestBody(required = false) PublishRequest req) {
        traceId = traceId(traceId);
        try {
            authService.authenticate(authorization);
            PublishResponse resp = catalogService.publish(toolId, req);
            return ApiResponse.ok(resp, traceId);
        } catch (GatewayException e) {
            return ApiResponse.error(e.getCode(), e.getCategory(), e.getMessage(), e.isRetriable(), traceId);
        }
    }

    /** 下线：→deprecated（状态机终点，不可再发布）。 */
    @PostMapping("/{toolId}/deprecate")
    public ApiResponse deprecate(@PathVariable String toolId,
                                 @RequestHeader(value = "Authorization", required = false) String authorization,
                                 @RequestHeader(value = "X-Trace-Id", required = false) String traceId) {
        traceId = traceId(traceId);
        try {
            authService.authenticate(authorization);
            PublishResponse resp = catalogService.deprecate(toolId);
            return ApiResponse.ok(resp, traceId);
        } catch (GatewayException e) {
            return ApiResponse.error(e.getCode(), e.getCategory(), e.getMessage(), e.isRetriable(), traceId);
        }
    }

    private String traceId(String traceId) {
        return (traceId == null || traceId.isEmpty()) ? GatewayUtil.id("trace") : traceId;
    }
}
