package com.allcli.gateway.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Agent 订阅（sys_subscription）。鉴权查 agentId×toolId 是否 approved + scope。
 */
@Entity
@Table(name = "sys_subscription")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Subscription {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id")
    private Long id;

    @Column(name = "sub_id", nullable = false, length = 64)
    private String subId;

    @Column(name = "agent_id", nullable = false, length = 64)
    private String agentId;

    @Column(name = "tool_id", nullable = false, length = 128)
    private String toolId;

    @Column(name = "scope", length = 128)
    private String scope;

    /** pending|approved|rejected|revoked */
    @Column(name = "status", nullable = false, length = 16)
    private String status;

    @Column(name = "approved_by", length = 64)
    private String approvedBy;
}
