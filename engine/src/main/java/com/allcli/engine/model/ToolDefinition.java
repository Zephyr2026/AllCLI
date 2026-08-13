package com.allcli.engine.model;

import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
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
 * 工具定义，与 {@code sys_tool_definition} 表一一对应。
 *
 * <p>JSON 列（endpoint/input_schema/output_schema/required_permission/hitl/examples）
 * 用 {@code Map<String,Object>} + {@link JdbcTypeCode}({@link SqlTypes#JSON}) 映射，
 * 与 OpenAPI JSON-Schema 动态结构契合、便于直接序列化输出。
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
    @JsonIgnore // 序列化输出时排除数据库自增 id（不属于工具语义）
    private Long id;

    /** 工具标识，如 create_ticket；operationId 优先，缺省由 METHOD_path sanitize 生成。 */
    @Column(name = "tool_id", nullable = false, length = 128)
    private String toolId;

    @Column(name = "name", nullable = false, length = 128)
    private String name;

    @Column(name = "version", nullable = false, length = 32)
    private String version;

    /** draft|reviewing|published|gray|deprecated */
    @Column(name = "status", nullable = false, length = 16)
    private String status;

    @Column(name = "system_id", nullable = false, length = 64)
    private String systemRef;

    @Column(name = "credential_id", length = 64)
    private String credentialRef;

    @Column(name = "description", columnDefinition = "TEXT")
    private String description;

    /** {method, path, timeoutMs} */
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "endpoint", nullable = false, columnDefinition = "json")
    private Map<String, Object> endpoint;

    /** JSON Schema */
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "input_schema", columnDefinition = "json")
    private Map<String, Object> inputSchema;

    /** JSON Schema（按 HTTP 状态码映射） */
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "output_schema", columnDefinition = "json")
    private Map<String, Object> outputSchema;

    /** readonly|write */
    @Column(name = "side_effect", nullable = false, length = 16)
    private String sideEffect;

    /** readonly|reversible|irreversible */
    @Column(name = "reversibility", nullable = false, length = 16)
    private String reversibility;

    @Column(name = "idempotency_key", length = 64)
    private String idempotencyKey;

    /** ["itsm:ticket:create"] */
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "required_permission", columnDefinition = "json")
    private List<String> requiredPermission;

    /** {enabled, approverType, approverRef, timeoutSec}；只读工具为 null */
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "hitl", columnDefinition = "json")
    private Map<String, Object> hitl;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "examples", columnDefinition = "json")
    private List<Map<String, Object>> examples;

    @Column(name = "owner", nullable = false, length = 64)
    private String owner;
}
