package com.allcli.gateway.catalog.api;

import com.allcli.gateway.api.dto.ApiResponse;
import com.allcli.gateway.api.dto.GatewayException;
import com.allcli.gateway.catalog.api.dto.SubscriptionCreateRequest;
import com.allcli.gateway.catalog.api.dto.SubscriptionResponse;
import com.allcli.gateway.catalog.core.SubscriptionService;
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
 * L3 订阅（FR-REG-03）：申请/审批。一期无 RBAC，审批人取 agent token 身份。
 * M4 追加管理面：GET 列表（admin）+ revoke（admin，Kill Switch 最小版）。
 */
@RestController
@RequestMapping("/api/v1/subscriptions")
public class SubscriptionController {

    private final SubscriptionService subscriptionService;
    private final AuthService authService;

    public SubscriptionController(SubscriptionService subscriptionService, AuthService authService) {
        this.subscriptionService = subscriptionService;
        this.authService = authService;
    }

    /** Agent 申请订阅（默认 pending）。 */
    @PostMapping
    public ApiResponse create(@RequestBody SubscriptionCreateRequest req,
                              @RequestHeader(value = "X-Trace-Id", required = false) String traceId) {
        traceId = traceId(traceId);
        try {
            return ApiResponse.ok(subscriptionService.create(req), traceId);
        } catch (GatewayException e) {
            return ApiResponse.error(e.getCode(), e.getCategory(), e.getMessage(), e.isRetriable(), traceId);
        }
    }

    /** 审批：approve → approved（即刻生效于网关鉴权）；reject → rejected。 */
    @PostMapping("/{subId}/approve")
    public ApiResponse approve(@PathVariable String subId,
                               @RequestHeader(value = "Authorization", required = false) String authorization,
                               @RequestHeader(value = "X-Trace-Id", required = false) String traceId) {
        return decide(subId, "approve", authorization, traceId);
    }

    @PostMapping("/{subId}/reject")
    public ApiResponse reject(@PathVariable String subId,
                              @RequestHeader(value = "Authorization", required = false) String authorization,
                              @RequestHeader(value = "X-Trace-Id", required = false) String traceId) {
        return decide(subId, "reject", authorization, traceId);
    }

    private ApiResponse decide(String subId, String decision, String authorization, String traceId) {
        traceId = traceId(traceId);
        try {
            String approver = authService.authenticate(authorization);
            SubscriptionResponse resp = subscriptionService.decide(subId, decision, approver);
            return ApiResponse.ok(resp, traceId);
        } catch (GatewayException e) {
            return ApiResponse.error(e.getCode(), e.getCategory(), e.getMessage(), e.isRetriable(), traceId);
        }
    }

    /** 管理面：按状态分页列表（?status=pending 等，status 空=全部）。鉴权：AdminAuthInterceptor（GET= admin）。 */
    @GetMapping
    public ApiResponse list(@RequestParam(required = false) String status,
                            @PageableDefault(size = 20) Pageable pageable,
                            @RequestHeader(value = "X-Trace-Id", required = false) String traceId) {
        traceId = traceId(traceId);
        try {
            Page<SubscriptionResponse> page = subscriptionService.list(status, pageable);
            return ApiResponse.ok(com.allcli.gateway.catalog.api.dto.PageResponse.of(page), traceId);
        } catch (GatewayException e) {
            return ApiResponse.error(e.getCode(), e.getCategory(), e.getMessage(), e.isRetriable(), traceId);
        }
    }

    /** 管理面：revoke（Kill Switch）——已批准订阅立即失效，后续 invoke 被拒。鉴权：AdminAuthInterceptor。 */
    @PostMapping("/{subId}/revoke")
    public ApiResponse revoke(@PathVariable String subId,
                              @RequestHeader(value = "X-Trace-Id", required = false) String traceId) {
        traceId = traceId(traceId);
        try {
            return ApiResponse.ok(subscriptionService.revoke(subId, "admin"), traceId);
        } catch (GatewayException e) {
            return ApiResponse.error(e.getCode(), e.getCategory(), e.getMessage(), e.isRetriable(), traceId);
        }
    }

    private String traceId(String traceId) {
        return (traceId == null || traceId.isEmpty()) ? GatewayUtil.id("trace") : traceId;
    }
}
