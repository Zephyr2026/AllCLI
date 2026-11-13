package com.allcli.gateway.admin.api;

import com.allcli.gateway.admin.api.dto.ApprovalEntry;
import com.allcli.gateway.api.dto.ApiResponse;
import com.allcli.gateway.catalog.api.dto.PageResponse;
import com.allcli.gateway.core.GatewayUtil;
import com.allcli.gateway.model.Approval;
import com.allcli.gateway.persistence.ApprovalRepository;
import com.allcli.gateway.persistence.InvocationRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * HITL 审批待办列表（admin 面，FR-GOV-04 配套工作台数据源）。
 * 待办量小，按 invocationId 逐条补 toolId/agentId（N+1 可接受）。
 * 决策动作仍走既有 POST /api/v1/hitl/{approvalId}/decision（approverRef 校验在网关）。
 */
@RestController
public class AdminApprovalController {

    private final ApprovalRepository approvalRepo;
    private final InvocationRepository invocationRepo;

    public AdminApprovalController(ApprovalRepository approvalRepo,
                                   InvocationRepository invocationRepo) {
        this.approvalRepo = approvalRepo;
        this.invocationRepo = invocationRepo;
    }

    /** 待办列表：decision 缺省 pending。 */
    @GetMapping("/api/v1/approvals")
    public ApiResponse list(@RequestParam(required = false) String decision,
                            @PageableDefault(size = 20) Pageable pageable,
                            @RequestHeader(value = "X-Trace-Id", required = false) String traceId) {
        String target = (decision == null || decision.isBlank()) ? "pending" : decision.trim();
        Page<ApprovalEntry> page = approvalRepo.findByDecision(target, pageable)
                .map(this::toEntry);
        return ApiResponse.ok(PageResponse.of(page),
                traceId == null || traceId.isEmpty() ? GatewayUtil.id("trace") : traceId);
    }

    private ApprovalEntry toEntry(Approval a) {
        return invocationRepo.findByInvocationId(a.getInvocationId())
                .map(inv -> ApprovalEntry.of(a, inv.getToolId(), inv.getAgentId()))
                .orElseGet(() -> ApprovalEntry.of(a, null, null));
    }
}
