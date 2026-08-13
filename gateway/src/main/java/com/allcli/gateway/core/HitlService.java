package com.allcli.gateway.core;

import com.allcli.gateway.api.dto.GatewayException;
import com.allcli.gateway.api.dto.HitlDecisionRequest;
import com.allcli.gateway.core.stages.AuditWriter;
import com.allcli.gateway.model.Approval;
import com.allcli.gateway.model.Invocation;
import com.allcli.gateway.model.ToolDefinition;
import com.allcli.gateway.persistence.ApprovalRepository;
import com.allcli.gateway.persistence.InvocationRepository;
import com.allcli.gateway.persistence.ToolDefinitionRepository;
import java.time.LocalDateTime;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * HITL 简化闸门：写操作人工确认。
 *
 * <p>挂起：pipeline 命中 HITL 时创建 Invocation(hitl_pending) + Approval(pending)，返回 HITL_PENDING。
 * 恢复：POST /hitl/{approvalId}/decision → approved 则置 Approval=approved、Invocation=success pending，
 *       由 pipeline 从第 7 步续跑（凭证注入→转发→审计）；rejected/timeout → HITL_REJECTED。
 * irreversible 必传 confirmIrreversible=true。
 */
@Service
public class HitlService {

    private final ApprovalRepository approvalRepo;
    private final InvocationRepository invocationRepo;
    private final ToolDefinitionRepository toolRepo;
    private final AuditWriter auditWriter;

    @Autowired
    public HitlService(ApprovalRepository approvalRepo, InvocationRepository invocationRepo,
                       ToolDefinitionRepository toolRepo, AuditWriter auditWriter) {
        this.approvalRepo = approvalRepo;
        this.invocationRepo = invocationRepo;
        this.toolRepo = toolRepo;
        this.auditWriter = auditWriter;
    }

    /** 取 Approval（供 pipeline.resume 用）。 */
    public Approval findApproval(String approvalId) {
        return approvalRepo.findByApprovalId(approvalId).orElseThrow(() ->
                GatewayException.of("HITL_REJECTED", "hitl", "审批单不存在: " + approvalId, false));
    }

    /** 挂起：创建 Approval(pending)，写入 Invocation(hitl_pending)。返回 approvalId。 */
    @Transactional
    public String suspend(InvokeContext ctx, boolean requireConfirm) {
        String approvalId = GatewayUtil.id("apv");
        ctx.setApprovalId(approvalId);
        ctx.setFinalStatus("hitl_pending");
        ctx.setInputDigest(GatewayUtil.digest(ctx.getRequest().getArguments()));

        // 先写 Invocation(hitl_pending)
        auditWriter.writePending(ctx);

        // 创建 Approval(pending)，approverRef 取 tool.hitl.approverRef
        String approverRef = approverRefOf(ctx.getTool());
        approvalRepo.save(Approval.builder()
                .approvalId(approvalId)
                .invocationId(ctx.getInvocationId())
                .approverRef(approverRef)
                .decision("pending")
                .createdAt(LocalDateTime.now())
                .build());
        return approvalId;
    }

    /**
     * 处理审批决定。
     * @return Invocation（approved 时状态仍 hitl_pending，由 pipeline 续跑后更新为 success）
     */
    @Transactional
    public Approval decide(String approvalId, HitlDecisionRequest req, String approver) {
        Approval approval = approvalRepo.findByApprovalId(approvalId).orElseThrow(() ->
                GatewayException.of("HITL_REJECTED", "hitl", "审批单不存在: " + approvalId, false));

        if (!"pending".equals(approval.getDecision())) {
            throw GatewayException.of("HITL_REJECTED", "hitl",
                    "审批单已处理: " + approval.getDecision(), false);
        }
        // approver 命中 approverRef（一期简单等值；生产对接权限体系）
        if (!approval.getApproverRef().equals(approver)) {
            throw GatewayException.of("AUTH_DENIED", "auth",
                    "无权审批该单（approver=" + approver + "）", false);
        }

        Invocation inv = invocationRepo.findByInvocationId(approval.getInvocationId())
                .orElseThrow(() -> GatewayException.of("HITL_REJECTED", "hitl", "关联调用不存在", false));
        ToolDefinition tool = toolRepo.findByToolId(inv.getToolId()).orElseThrow();

        if ("rejected".equalsIgnoreCase(req.getDecision())) {
            approval.setDecision("rejected");
            approval.setReason(req.getReason());
            approval.setDecidedAt(LocalDateTime.now());
            approvalRepo.save(approval);
            inv.setStatus("fail");
            inv.setErrorCode("HITL_REJECTED");
            invocationRepo.save(inv);
            throw GatewayException.of("HITL_REJECTED", "hitl", "审批被拒绝", false);
        }

        // approved
        if ("irreversible".equals(tool.getReversibility())
                && !Boolean.TRUE.equals(req.getConfirmIrreversible())) {
            throw GatewayException.of("HITL_REJECTED", "hitl",
                    "不可逆操作需 confirmIrreversible=true", false);
        }
        approval.setDecision("approved");
        approval.setReason(req.getReason());
        approval.setDecidedAt(LocalDateTime.now());
        return approvalRepo.save(approval);
    }

    @SuppressWarnings("unchecked")
    private String approverRefOf(ToolDefinition tool) {
        if (tool.getHitl() == null) {
            return tool.getOwner();
        }
        Object ref = tool.getHitl().get("approverRef");
        return ref != null ? String.valueOf(ref) : tool.getOwner();
    }
}
