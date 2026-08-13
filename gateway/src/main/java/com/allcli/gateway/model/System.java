package com.allcli.gateway.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.util.Map;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

/** 存量系统（sys_system）。网关取 base_url/auth_type 转发调用。 */
@Entity
@Table(name = "sys_system")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class System {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id")
    private Long id;

    @Column(name = "system_id", nullable = false, length = 64)
    private String systemId;

    @Column(name = "name", nullable = false, length = 128)
    private String name;

    @Column(name = "base_url", nullable = false, length = 512)
    private String baseUrl;

    /** OAuth2|ApiKey|Cookie|None */
    @Column(name = "auth_type", nullable = false, length = 32)
    private String authType;

    @Column(name = "owner", nullable = false, length = 64)
    private String owner;

    @Column(name = "status", nullable = false, length = 16)
    private String status;

    /** 认证细节（非密钥） */
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "extra", columnDefinition = "json")
    private Map<String, Object> extra;
}
