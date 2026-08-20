package com.allcli.gateway.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.LocalDateTime;
import java.util.Map;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

/**
 * 工具版本快照（映射 sys_tool_version）。发布时写入完整定义快照，支撑回滚（FR-REG-01）。
 */
@Entity
@Table(name = "sys_tool_version")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ToolVersion {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id")
    private Long id;

    @Column(name = "tool_id", nullable = false, length = 128)
    private String toolId;

    @Column(name = "version", nullable = false, length = 32)
    private String version;

    @Column(name = "changelog", columnDefinition = "TEXT")
    private String changelog;

    /** 发布时的完整定义快照（ToolDefinition 全字段 JSON）。 */
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "definition_snapshot", columnDefinition = "json")
    private Map<String, Object> definitionSnapshot;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;
}
