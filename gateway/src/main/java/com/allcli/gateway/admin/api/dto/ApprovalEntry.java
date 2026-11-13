package com.allcli.gateway.admin.api.dto;

import com.allcli.gateway.model.Approval;
import java.time.LocalDateTime;
import lombok.Builder;
import lombok.Getter;

/** HITL 审批条目（管理面列表用，按 invocation 补齐 toolId/agentId）。 */
@Getter
@Builder
public class ApprovalEntry {

    private String approvalId;

    private String invocationId;

    private String toolId;

    private String agentId;

    private String approverRef;

    private String reason;

    private LocalDateTime createdAt;

    public static ApprovalEntry of(Approval a, String toolId, String agentId) {
        return ApprovalEntry.builder()
                .approvalId(a.getApprovalId())
                .invocationId(a.getInvocationId())
                .toolId(toolId)
                .agentId(agentId)
                .approverRef(a.getApproverRef())
                .reason(a.getReason())
                .createdAt(a.getCreatedAt())
                .build();
    }
}
