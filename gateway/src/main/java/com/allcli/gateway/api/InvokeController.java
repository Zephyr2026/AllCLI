package com.allcli.gateway.api;

import com.allcli.gateway.api.dto.ApiResponse;
import com.allcli.gateway.api.dto.GatewayException;
import com.allcli.gateway.api.dto.HitlDecisionRequest;
import com.allcli.gateway.api.dto.InvokeData;
import com.allcli.gateway.api.dto.InvokeRequest;
import com.allcli.gateway.core.GatewayUtil;
import com.allcli.gateway.core.HitlService;
import com.allcli.gateway.core.InvokePipeline;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** 网关调用入口。 */
@RestController
@RequestMapping("/api/v1")
public class InvokeController {

    private final InvokePipeline pipeline;
    private final HitlService hitlService;

    public InvokeController(InvokePipeline pipeline, HitlService hitlService) {
        this.pipeline = pipeline;
        this.hitlService = hitlService;
    }

    @PostMapping("/invoke")
    public ApiResponse invoke(@RequestHeader(value = "Authorization", required = false) String authorization,
                              @RequestHeader(value = "X-Trace-Id", required = false) String traceId,
                              @RequestBody InvokeRequest request) {
        if (traceId == null || traceId.isEmpty()) {
            traceId = GatewayUtil.id("trace");
        }
        String agentId;
        try {
            agentId = extractAgent(authorization);
        } catch (GatewayException e) {
            return ApiResponse.error(e.getCode(), e.getCategory(), e.getMessage(), e.isRetriable(), traceId);
        }
        InvokeData data = pipeline.execute(traceId, agentId, request);
        return ApiResponse.ok(data, traceId);
    }

    /** HITL 审批决定：approved → 续跑后半段；rejected → HITL_REJECTED。 */
    @PostMapping("/hitl/{approvalId}/decision")
    public ApiResponse decide(@PathVariable String approvalId,
                              @RequestHeader(value = "Authorization", required = false) String authorization,
                              @RequestHeader(value = "X-Trace-Id", required = false) String traceId,
                              @RequestBody HitlDecisionRequest req) {
        if (traceId == null || traceId.isEmpty()) {
            traceId = GatewayUtil.id("trace");
        }
        String approver;
        try {
            approver = extractAgent(authorization);
            hitlService.decide(approvalId, req, approver);
            InvokeData data = pipeline.resume(approvalId);
            return ApiResponse.ok(data, traceId);
        } catch (GatewayException e) {
            return ApiResponse.error(e.getCode(), e.getCategory(), e.getMessage(), e.isRetriable(), traceId);
        }
    }

    private String extractAgent(String authorization) {
        if (authorization == null || !authorization.startsWith("Bearer agent_")) {
            throw GatewayException.of("AUTH_DENIED", "auth", "缺少或非法的 Authorization 头", false);
        }
        return authorization.substring("Bearer agent_".length()).trim();
    }
}
