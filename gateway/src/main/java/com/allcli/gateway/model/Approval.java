package com.allcli.gateway.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.LocalDateTime;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * HITL 审批（sys_approval）。简化闸门：写操作人工确认。
 * decision 流转：pending → approved | rejected | timeout。
 */
@Entity
@Table(name = "sys_approval")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Approval {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id")
    private Long id;

    @Column(name = "approval_id", nullable = false, length = 64)
    private String approvalId;

    @Column(name = "invocation_id", nullable = false, length = 64)
    private String invocationId;

    @Column(name = "approver_ref", nullable = false, length = 64)
    private String approverRef;

    /** pending|approved|rejected|timeout */
    @Column(name = "decision", nullable = false, length = 16)
    private String decision;

    @Column(name = "reason", length = 512)
    private String reason;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    @Column(name = "decided_at")
    private LocalDateTime decidedAt;
}
