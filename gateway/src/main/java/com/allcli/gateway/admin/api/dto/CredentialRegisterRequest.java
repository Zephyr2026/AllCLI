package com.allcli.gateway.admin.api.dto;

import lombok.Getter;
import lombok.Setter;

/** POST /api/v1/credentials 请求体（FR-CONV-04：凭证托管）。 */
@Getter
@Setter
public class CredentialRegisterRequest {

    /** 凭证所属系统（须已注册）。 */
    private String systemRef;

    /** Vault|KMS|Opaque */
    private String type;

    /** 凭证明文（仅本次请求内存中存在，信封加密后只存 secret_ref，不回显）。 */
    private String secret;

    /** 轮转策略（如 30d，仅记录）。 */
    private String rotatePolicy;
}
