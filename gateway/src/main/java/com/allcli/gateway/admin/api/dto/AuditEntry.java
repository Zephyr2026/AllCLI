package com.allcli.gateway.admin.api.dto;

import com.allcli.gateway.model.Invocation;
import java.time.LocalDateTime;
import lombok.Builder;
import lombok.Getter;

/** 审计列表项（脱敏摘要：仅元信息，无入参出参——DB 本就只存 digest）。 */
@Getter
@Builder
public class AuditEntry {

    private LocalDateTime createdAt;

    private String invocationId;

    private String traceId;

    private String agentId;

    private String toolId;

    /** success|fail|hitl_pending|timeout */
    private String status;

    private Integer durationMs;

    private String errorCode;

    private String approvalId;

    public static AuditEntry of(Invocation i) {
        return AuditEntry.builder()
                .createdAt(i.getCreatedAt())
                .invocationId(i.getInvocationId())
                .traceId(i.getTraceId())
                .agentId(i.getAgentId())
                .toolId(i.getToolId())
                .status(i.getStatus())
                .durationMs(i.getDurationMs())
                .errorCode(i.getErrorCode())
                .approvalId(i.getApprovalId())
                .build();
    }
}
