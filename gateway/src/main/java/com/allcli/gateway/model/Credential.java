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
 * 凭证（sys_credential）。secret_ref 为密文引用（一期 allcli-vault:// 格式），明文永不入库。
 */
@Entity
@Table(name = "sys_credential")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Credential {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id")
    private Long id;

    @Column(name = "cred_id", nullable = false, length = 64)
    private String credId;

    @Column(name = "system_id", nullable = false, length = 64)
    private String systemRef;

    /** Vault|KMS|Opaque */
    @Column(name = "type", nullable = false, length = 32)
    private String type;

    /** 密文引用，如 allcli-vault://dek/{wrappedDek}#{ciphertext} */
    @Column(name = "secret_ref", nullable = false, length = 512)
    private String secretRef;

    @Column(name = "rotate_policy", length = 64)
    private String rotatePolicy;

    @Column(name = "status", nullable = false, length = 16)
    private String status;
}
