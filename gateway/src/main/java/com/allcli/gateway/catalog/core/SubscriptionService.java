package com.allcli.gateway.catalog.core;

import com.allcli.gateway.api.dto.GatewayException;
import com.allcli.gateway.catalog.api.dto.SubscriptionCreateRequest;
import com.allcli.gateway.catalog.api.dto.SubscriptionResponse;
import com.allcli.gateway.core.GatewayUtil;
import com.allcli.gateway.model.Subscription;
import com.allcli.gateway.persistence.SubscriptionRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 订阅服务（FR-REG-03）：Agent 申请（默认 pending）→ owner 审批 approve/reject。
 * 审批通过的订阅即刻生效于网关鉴权（AuthService 查 approved）。
 */
@Service
public class SubscriptionService {

    private final SubscriptionRepository subRepo;
    private final CatalogService catalogService;

    public SubscriptionService(SubscriptionRepository subRepo, CatalogService catalogService) {
        this.subRepo = subRepo;
        this.catalogService = catalogService;
    }

    @Transactional
    public SubscriptionResponse create(SubscriptionCreateRequest req) {
        if (req == null || isBlank(req.getAgentId()) || isBlank(req.getToolId())) {
            throw GatewayException.of("VALIDATION_ERROR", "validation",
                    "agentId 与 toolId 必填", false);
        }
        catalogService.requireTool(req.getToolId().trim());
        if (subRepo.findByAgentIdAndToolId(req.getAgentId().trim(), req.getToolId().trim()).isPresent()) {
            throw GatewayException.of("SUB_ALREADY_EXISTS", "business",
                    "订阅已存在: agent=" + req.getAgentId() + " tool=" + req.getToolId(), false);
        }
        Subscription sub = subRepo.save(Subscription.builder()
                .subId(GatewayUtil.id("sub"))
                .agentId(req.getAgentId().trim())
                .toolId(req.getToolId().trim())
                .scope(req.getScope())
                .status("pending")
                .build());
        return toResponse(sub);
    }

    /** 审批：pending → approved/rejected。 */
    @Transactional
    public SubscriptionResponse decide(String subId, String decision, String approver) {
        Subscription sub = subRepo.findBySubId(subId)
                .orElseThrow(() -> GatewayException.of("SUB_NOT_FOUND", "validation",
                        "订阅不存在: " + subId, false));
        if (!"pending".equals(sub.getStatus())) {
            throw GatewayException.of("INVALID_STATUS_TRANSITION", "business",
                    "订阅已处理，当前状态: " + sub.getStatus(), false);
        }
        if (!"approve".equals(decision) && !"reject".equals(decision)) {
            throw GatewayException.of("VALIDATION_ERROR", "validation",
                    "decision 仅支持 approve/reject", false);
        }
        sub.setStatus("approve".equals(decision) ? "approved" : "rejected");
        sub.setApprovedBy(approver);
        return toResponse(subRepo.save(sub));
    }

    private static boolean isBlank(String s) {
        return s == null || s.isBlank();
    }

    private static SubscriptionResponse toResponse(Subscription s) {
        return SubscriptionResponse.builder()
                .subId(s.getSubId())
                .agentId(s.getAgentId())
                .toolId(s.getToolId())
                .scope(s.getScope())
                .status(s.getStatus())
                .approvedBy(s.getApprovedBy())
                .build();
    }
}
