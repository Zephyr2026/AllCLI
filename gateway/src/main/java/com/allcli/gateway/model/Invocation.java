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
 * 调用记录元信息（sys_invocation）。明细审计入 ClickHouse（后续）；明文不入库，仅存 digest。
 */
@Entity
@Table(name = "sys_invocation")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Invocation {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id")
    private Long id;

    @Column(name = "invocation_id", nullable = false, length = 64)
    private String invocationId;

    @Column(name = "trace_id", length = 64)
    private String traceId;

    @Column(name = "agent_id", nullable = false, length = 64)
    private String agentId;

    @Column(name = "tool_id", nullable = false, length = 128)
    private String toolId;

    /** 输入 SHA256 hex */
    @Column(name = "input_digest", length = 128)
    private String inputDigest;

    /** 输出 SHA256 hex */
    @Column(name = "output_digest", length = 128)
    private String outputDigest;

    /** success|fail|hitl_pending|timeout */
    @Column(name = "status", nullable = false, length = 16)
    private String status;

    @Column(name = "duration_ms")
    private Integer durationMs;

    @Column(name = "approval_id", length = 64)
    private String approvalId;

    @Column(name = "error_code", length = 32)
    private String errorCode;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;
}
