package com.allcli.gateway.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import java.util.List;
import java.util.Map;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

/**
 * 工具定义只读副本（映射 sys_tool_definition），网关按 tool_id/version 解析调用所需元数据。
 * 字段与 engine 模块对齐；网关侧只读不写。
 */
@Entity
@Table(name = "sys_tool_definition")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ToolDefinition {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id")
    private Long id;

    @Column(name = "tool_id", nullable = false, length = 128)
    private String toolId;

    @Column(name = "name", nullable = false, length = 128)
    private String name;

    @Column(name = "version", nullable = false, length = 32)
    private String version;

    @Column(name = "status", nullable = false, length = 16)
    private String status;

    @Column(name = "system_id", nullable = false, length = 64)
    private String systemRef;

    @Column(name = "credential_id", length = 64)
    private String credentialRef;

    @Column(name = "description", columnDefinition = "TEXT")
    private String description;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "endpoint", columnDefinition = "json")
    private Map<String, Object> endpoint;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "input_schema", columnDefinition = "json")
    private Map<String, Object> inputSchema;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "output_schema", columnDefinition = "json")
    private Map<String, Object> outputSchema;

    @Column(name = "side_effect", nullable = false, length = 16)
    private String sideEffect;

    @Column(name = "reversibility", nullable = false, length = 16)
    private String reversibility;

    @Column(name = "idempotency_key", length = 64)
    private String idempotencyKey;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "required_permission", columnDefinition = "json")
    private List<String> requiredPermission;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "hitl", columnDefinition = "json")
    private Map<String, Object> hitl;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "examples", columnDefinition = "json")
    private List<Map<String, Object>> examples;

    @Column(name = "owner", nullable = false, length = 64)
    private String owner;
}
