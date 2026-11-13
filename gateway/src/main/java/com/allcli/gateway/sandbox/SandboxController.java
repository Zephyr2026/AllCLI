package com.allcli.gateway.sandbox;

import com.allcli.gateway.api.dto.ApiResponse;
import com.allcli.gateway.api.dto.GatewayException;
import com.allcli.gateway.catalog.core.CatalogService;
import com.allcli.gateway.core.GatewayUtil;
import com.allcli.gateway.core.stages.AuthService;
import com.allcli.gateway.sandbox.dto.QualityReport;
import com.allcli.gateway.sandbox.dto.SandboxRequest;
import com.allcli.gateway.sandbox.dto.SandboxResponse;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 沙箱试运行（FR-AGENT-03）与语义质量自检（FR-SEM-05）。
 *
 * <p>上线前自检入口，面向工具 owner/开发：需 agent token 标识操作人，
 * 但不走订阅校验（draft 工具无需订阅即可试）。
 */
@RestController
@RequestMapping("/api/v1/tools")
public class SandboxController {

    private final SandboxService sandboxService;
    private final CatalogService catalogService;
    private final AuthService authService;

    public SandboxController(SandboxService sandboxService, CatalogService catalogService,
                             AuthService authService) {
        this.sandboxService = sandboxService;
        this.catalogService = catalogService;
        this.authService = authService;
    }

    /** 沙箱试运行：mock 凭证 + 示例参数演练，不触达真实系统。 */
    @PostMapping("/{toolId}/sandbox")
    public ApiResponse sandbox(@PathVariable String toolId,
                               @RequestHeader(value = "Authorization", required = false) String authorization,
                               @RequestHeader(value = "X-Trace-Id", required = false) String traceId,
                               @RequestBody(required = false) SandboxRequest req) {
        traceId = traceId(traceId);
        try {
            authService.authenticate(authorization);
            SandboxResponse resp = sandboxService.run(
                    catalogService.requireTool(toolId), req == null ? null : req.getArguments());
            return ApiResponse.ok(resp, traceId);
        } catch (GatewayException e) {
            return ApiResponse.error(e.getCode(), e.getCategory(), e.getMessage(), e.isRetriable(), traceId);
        }
    }

    /** 质量自检 checklist（advisory，不拦截发布）。 */
    @PostMapping("/{toolId}/quality")
    public ApiResponse quality(@PathVariable String toolId,
                               @RequestHeader(value = "Authorization", required = false) String authorization,
                               @RequestHeader(value = "X-Trace-Id", required = false) String traceId) {
        traceId = traceId(traceId);
        try {
            authService.authenticate(authorization);
            QualityReport report = sandboxService.quality(catalogService.requireTool(toolId));
            return ApiResponse.ok(report, traceId);
        } catch (GatewayException e) {
            return ApiResponse.error(e.getCode(), e.getCategory(), e.getMessage(), e.isRetriable(), traceId);
        }
    }

    private String traceId(String traceId) {
        return (traceId == null || traceId.isEmpty()) ? GatewayUtil.id("trace") : traceId;
    }
}
